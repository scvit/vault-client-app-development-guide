#include "vault_client.h"
#include "config.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

// HTTP 응답을 저장할 구조체
struct http_response {
    char *data;
    size_t size;
};

// libcurl 콜백 함수
static size_t write_callback(void *contents, size_t size, size_t nmemb, struct http_response *response) {
    size_t total_size = size * nmemb;
    response->data = realloc(response->data, response->size + total_size + 1);
    
    if (response->data) {
        memcpy(&(response->data[response->size]), contents, total_size);
        response->size += total_size;
        response->data[response->size] = 0;
    }
    
    return total_size;
}

// Vault 클라이언트 초기화
int vault_client_init(vault_client_t *client, app_config_t *config) {
    if (!client || !config) return -1;
    
    // 설정 참조 저장
    client->config = config;
    
    // URL 설정
    strncpy(client->vault_url, config->vault_url, sizeof(client->vault_url) - 1);
    client->vault_url[sizeof(client->vault_url) - 1] = '\0';
    
    // CURL 초기화
    client->curl = curl_easy_init();
    if (!client->curl) {
        fprintf(stderr, "Failed to initialize CURL\n");
        return -1;
    }
    
    // CURL 옵션 설정 (설정에서 가져옴)
    curl_easy_setopt(client->curl, CURLOPT_TIMEOUT, config->http_timeout);
    curl_easy_setopt(client->curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(client->curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(client->curl, CURLOPT_SSL_VERIFYHOST, 0L);


    
    client->token[0] = '\0';
    client->token_expiry = 0;
    client->token_issued = 0;
    
    // KV 캐시 초기화
    client->cached_kv_secret = NULL;
    client->kv_last_refresh = 0;
    client->kv_path[0] = '\0';
    client->kv_version = -1;  // 초기 버전은 -1 (캐시 없음)
    
    // Database Dynamic 캐시 초기화
    client->cached_db_dynamic_secret = NULL;
    client->db_dynamic_last_refresh = 0;
    client->db_dynamic_path[0] = '\0';
    client->lease_id[0] = '\0';
    client->lease_expiry = 0;
    
    // Database Static 캐시 초기화
    client->cached_db_static_secret = NULL;
    client->db_static_last_refresh = 0;
    client->db_static_path[0] = '\0';
    
    // KV 경로 설정 (Entity 기반)
    if (config->secret_kv.enabled && config->secret_kv.kv_path[0]) {
        snprintf(client->kv_path, sizeof(client->kv_path), "%s-kv/data/%s", 
                config->entity, config->secret_kv.kv_path);
    }
    
    // Database Dynamic 경로 설정 (Entity 기반)
    if (config->secret_database_dynamic.enabled && config->secret_database_dynamic.role_id[0]) {
        snprintf(client->db_dynamic_path, sizeof(client->db_dynamic_path), "%s-database/creds/%s", 
                config->entity, config->secret_database_dynamic.role_id);
    }
    
    // Database Static 경로 설정 (Entity 기반)
    if (config->secret_database_static.enabled && config->secret_database_static.role_id[0]) {
        snprintf(client->db_static_path, sizeof(client->db_static_path), "%s-database/static-creds/%s", 
                config->entity, config->secret_database_static.role_id);
    }
    
    return 0;
}

// Vault 클라이언트 정리
void vault_client_cleanup(vault_client_t *client) {
    if (client) {
        if (client->curl) {
            curl_easy_cleanup(client->curl);
            client->curl = NULL;
        }
        
        // KV 캐시 정리
        vault_cleanup_kv_cache(client);
        
        // Database Dynamic 캐시 정리
        vault_cleanup_db_dynamic_cache(client);
        
        // Database Static 캐시 정리
        vault_cleanup_db_static_cache(client);
    }
}

// AppRole 로그인
int vault_login(vault_client_t *client, const char *role_id, const char *secret_id) {
    if (!client || !role_id || !secret_id) return -1;
    
    // 새로운 CURL 핸들 생성
    CURL *curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "Failed to initialize CURL\n");
        return -1;
    }
    
    // JSON 요청 생성
    json_object *request = json_object_new_object();
    json_object_object_add(request, "role_id", json_object_new_string(role_id));
    json_object_object_add(request, "secret_id", json_object_new_string(secret_id));
    
    char *json_string = (char*)json_object_to_json_string(request);
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_string);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, strlen(json_string));
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, client->config->http_timeout);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/auth/approle/login", client->vault_url);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    
    // Content-Type 헤더 설정
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
     
    
    // 네임스페이스 헤더 추가
    if (client->config && client->config->vault_namespace[0]) {
    char ns_header[256];
    snprintf(ns_header, sizeof(ns_header), "X-Vault-Namespace: %s", 
            client->config->vault_namespace);
    headers = curl_slist_append(headers, ns_header);
   }


    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    // 요청 실행
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    json_object_put(request);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Login request failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 응답 파싱
    // printf("🔍 Login response: %s\n", response.data);
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse login response\n");
        free(response.data);
        return -1;
    }
    
    // 토큰 추출
    json_object *auth, *client_token;
    if (json_object_object_get_ex(json_response, "auth", &auth) &&
        json_object_object_get_ex(auth, "client_token", &client_token)) {
        
        const char *token = json_object_get_string(client_token);
        strncpy(client->token, token, sizeof(client->token) - 1);
        client->token[sizeof(client->token) - 1] = '\0';
        
        // 토큰 발급 시간 기록
        client->token_issued = time(NULL);
        
        // 토큰 만료 시간 설정 (Vault에서 받은 실제 TTL 사용)
        json_object *lease_duration;
        if (json_object_object_get_ex(auth, "lease_duration", &lease_duration)) {
            int ttl_seconds = json_object_get_int(lease_duration);
            client->token_expiry = client->token_issued + ttl_seconds;
            printf("Token TTL from Vault: %d seconds\n", ttl_seconds);
        } else {
            // TTL 정보가 없으면 기본값 사용 (1시간)
            client->token_expiry = client->token_issued + 3600;
            printf("Warning: No TTL info from Vault, using default 1 hour\n");
        }
        
        printf("Login successful. Token expires in %ld seconds\n", 
               client->token_expiry - time(NULL));
    } else {
        fprintf(stderr, "Failed to extract token from response\n");
        json_object_put(json_response);
        free(response.data);
        return -1;
    }
    
    json_object_put(json_response);
    free(response.data);
    curl_easy_cleanup(curl);
    return 0;
}

// 토큰 갱신
int vault_renew_token(vault_client_t *client) {
    if (!client || !client->token[0]) return -1;
    
    // 새로운 CURL 핸들 생성
    CURL *curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "Failed to initialize CURL for renewal\n");
        return -1;
    }
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, "");
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, 0);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, client->config->http_timeout);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/auth/token/renew-self", client->vault_url);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    
    // Authorization 헤더 설정
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, auth_header);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    // 요청 실행
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Token renewal failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // HTTP 상태 코드 확인
    long http_code;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    

    if (http_code != 200) {
        fprintf(stderr, "Token renewal failed with HTTP %ld\n", http_code);
        printf("Response: %s\n", response.data);
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 응답 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (json_response) {
        json_object *auth, *lease_duration;
        if (json_object_object_get_ex(json_response, "auth", &auth) &&
            json_object_object_get_ex(auth, "lease_duration", &lease_duration)) {
            
            int lease_seconds = json_object_get_int(lease_duration);
            time_t now = time(NULL);
            client->token_issued = now;  // 갱신 시간 업데이트
            client->token_expiry = now + lease_seconds;
            
            printf("Token renewed successfully. New expiry: %ld seconds\n", 
                   client->token_expiry - now);
        } else {
            printf("Warning: No lease_duration in renewal response\n");
            // 응답 내용 출력 (디버깅용)
            printf("Renewal response: %s\n", response.data);
        }
        json_object_put(json_response);
    } else {
        printf("Warning: Failed to parse renewal response\n");
        printf("Renewal response: %s\n", response.data);
    }
    
    free(response.data);
    curl_easy_cleanup(curl);
    return 0;
}

// 시크릿 가져오기
int vault_get_secret(vault_client_t *client, const char *path, json_object **secret_data) {
    if (!client || !path || !secret_data) return -1;
    
    // 새로운 CURL 핸들 생성
    CURL *curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "Failed to initialize CURL for secret\n");
        return -1;
    }
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, client->config->http_timeout);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/%s", client->vault_url, path);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    
    // Authorization 헤더 설정
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, auth_header);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    // 요청 실행
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Secret request failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 응답 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse secret response\n");
        free(response.data);
        return -1;
    }
    
    // 시크릿 데이터 추출
    json_object *data, *data_obj;
    if (json_object_object_get_ex(json_response, "data", &data) &&
        json_object_object_get_ex(data, "data", &data_obj)) {
        
        *secret_data = json_object_get(data_obj); // 참조 카운트 증가
        
        printf("Secret retrieved successfully\n");
    } else {
        fprintf(stderr, "Failed to extract secret data\n");
        json_object_put(json_response);
        free(response.data);
        return -1;
    }
    
    // json_response 해제 (secret_data는 별도 참조이므로 안전)
    json_object_put(json_response);
    free(response.data);
    curl_easy_cleanup(curl);
    return 0;
}

// 토큰 유효성 확인
int vault_is_token_valid(vault_client_t *client) {
    if (!client || !client->token[0]) return 0;
    
    time_t now = time(NULL);
    time_t total_ttl = client->token_expiry - client->token_issued;
    time_t elapsed = now - client->token_issued;
    time_t renewal_point = total_ttl * 4 / 5;  // 4/5 지점에서 갱신 필요
    
    return (elapsed < renewal_point);
}

// 토큰 남은 시간 출력
void vault_print_token_status(vault_client_t *client) {
    if (!client || !client->token[0] || !client->config) return;
    
    time_t now = time(NULL);
    time_t remaining = client->token_expiry - now;
    
    if (remaining > 0) {
        printf("Token status: %ld seconds remaining (expires in %ld minutes)\n", 
               remaining, remaining / 60);
        
        // 갱신 권장 시점 계산 (4/5 지점 기준)
        time_t total_ttl = client->token_expiry - client->token_issued;
        time_t elapsed = time(NULL) - client->token_issued;
        time_t renewal_point = total_ttl * 4 / 5;  // 4/5 지점
        time_t urgent_point = total_ttl * 9 / 10;  // 9/10 지점
        
        if (elapsed >= urgent_point) {
            printf("⚠️  URGENT: Token should be renewed soon (at %ld%% of TTL)\n", 
                   (elapsed * 100) / total_ttl);
        } else if (elapsed >= renewal_point) {
            printf("🔄 Token renewal recommended (at %ld%% of TTL)\n", 
                   (elapsed * 100) / total_ttl);
        } else {
            printf("✅ Token is healthy (at %ld%% of TTL)\n", 
                   (elapsed * 100) / total_ttl);
        }
    } else {
        printf("❌ Token has expired!\n");
    }
}

// 시크릿 데이터 정리
void vault_cleanup_secret(json_object *secret_data) {
    if (secret_data) {
        json_object_put(secret_data);
    }
}

// KV 시크릿 갱신 (버전 기반)
int vault_refresh_kv_secret(vault_client_t *client) {
    if (!client || !client->config || !client->config->secret_kv.enabled) {
        return -1;
    }
    
    if (!client->kv_path[0]) {
        fprintf(stderr, "KV path not configured\n");
        return -1;
    }
    
    printf("🔄 Refreshing KV secret from path: %s\n", client->kv_path);
    
    // 새로운 시크릿 가져오기 (전체 응답을 위해 직접 HTTP 요청)
    json_object *new_secret = NULL;
    int result = vault_get_kv_secret_direct(client, &new_secret);
    
    if (result == 0 && new_secret) {
        // 버전 정보 추출
        json_object *data, *metadata, *version_obj;
        int new_version = -1;
        
        if (json_object_object_get_ex(new_secret, "data", &data) &&
            json_object_object_get_ex(data, "metadata", &metadata) &&
            json_object_object_get_ex(metadata, "version", &version_obj)) {
            
            new_version = json_object_get_int(version_obj);
        }
        
        // 버전이 다르거나 캐시가 없는 경우에만 업데이트
        if (new_version != client->kv_version) {
            // 기존 캐시 정리
            vault_cleanup_kv_cache(client);
            
            // 캐시 업데이트
            client->cached_kv_secret = json_object_get(new_secret);
            client->kv_last_refresh = time(NULL);
            client->kv_version = new_version;
            
            printf("✅ KV secret updated (version: %d)\n", new_version);
        } else {
            printf("✅ KV secret unchanged (version: %d)\n", new_version);
            client->kv_last_refresh = time(NULL);  // 마지막 확인 시간 업데이트
        }
        
        // 임시 객체 정리
        json_object_put(new_secret);
        return 0;
    } else {
        fprintf(stderr, "❌ Failed to refresh KV secret\n");
        return -1;
    }
}

// KV 시크릿 가져오기 (캐시 확인)
int vault_get_kv_secret(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data || !client->config || !client->config->secret_kv.enabled) {
        return -1;
    }
    
    // 캐시가 없거나 오래된 경우 갱신
    if (!client->cached_kv_secret || vault_is_kv_secret_stale(client)) {
        printf("🔄 KV cache is stale, refreshing...\n");
        if (vault_refresh_kv_secret(client) != 0) {
            return -1;
        }
    }
    
    // 캐시된 데이터 반환
    if (client->cached_kv_secret) {
        *secret_data = json_object_get(client->cached_kv_secret);
        return 0;
    }
    
    return -1;
}

// KV 시크릿이 오래되었는지 확인 (버전 기반)
int vault_is_kv_secret_stale(vault_client_t *client) {
    if (!client || !client->config) {
        return 1;  // 설정이 없으면 오래된 것으로 간주
    }
    
    // 캐시가 없으면 항상 갱신 필요
    if (!client->cached_kv_secret) {
        return 1;
    }
    
    // 버전 기반 갱신: 항상 최신 버전 확인
    // KV v2는 버전 정보를 제공하므로 시간 기반이 아닌 버전 기반으로 갱신
    return 1;  // 항상 버전 확인을 위해 갱신 시도
}

// KV 캐시 정리
void vault_cleanup_kv_cache(vault_client_t *client) {
    if (client && client->cached_kv_secret) {
        json_object_put(client->cached_kv_secret);
        client->cached_kv_secret = NULL;
        client->kv_last_refresh = 0;
        client->kv_version = -1;  // 버전도 초기화
    }
}

// Database Dynamic 시크릿 갱신
int vault_refresh_db_dynamic_secret(vault_client_t *client) {
    if (!client || !client->config || !client->config->secret_database_dynamic.enabled) {
        return -1;
    }
    
    if (!client->db_dynamic_path[0]) {
        fprintf(stderr, "Database Dynamic path not configured\n");
        return -1;
    }
    
    printf("🔄 Refreshing Database Dynamic secret from path: %s\n", client->db_dynamic_path);
    
    // 기존 캐시가 있는 경우 TTL 확인
    if (client->cached_db_dynamic_secret && strlen(client->lease_id) > 0) {
        time_t expire_time;
        int ttl;
        if (vault_check_lease_status(client, client->lease_id, &expire_time, &ttl) == 0) {
            // TTL이 충분히 남아있으면 갱신하지 않음
            if (ttl > 10) {  // 10초 이상 남아있으면 갱신하지 않음
                printf("✅ Database Dynamic secret is still valid (TTL: %d seconds)\n", ttl);
                client->db_dynamic_last_refresh = time(NULL);
                return 0;
            } else {
                printf("⚠️ Database Dynamic secret expiring soon (TTL: %d seconds), creating new credentials\n", ttl);
            }
        }
    }
    
    // 기존 캐시 정리
    vault_cleanup_db_dynamic_cache(client);
    
    // 새로운 Database Dynamic 시크릿 생성
    json_object *new_secret = NULL;
    int result = vault_get_db_dynamic_secret_direct(client, &new_secret);
    
    if (result == 0 && new_secret) {
        // lease_id 추출
        json_object *lease_id_obj;
        if (json_object_object_get_ex(new_secret, "lease_id", &lease_id_obj)) {
            const char *lease_id = json_object_get_string(lease_id_obj);
            strncpy(client->lease_id, lease_id, sizeof(client->lease_id) - 1);
            client->lease_id[sizeof(client->lease_id) - 1] = '\0';
        }
        
        // 캐시 업데이트
        client->cached_db_dynamic_secret = json_object_get(new_secret);
        client->db_dynamic_last_refresh = time(NULL);
        
        // lease 만료 시간 확인
        time_t expire_time;
        int ttl = 0;
        if (strlen(client->lease_id) > 0 && vault_check_lease_status(client, client->lease_id, &expire_time, &ttl) == 0) {
            client->lease_expiry = expire_time;
        }
        
        printf("✅ Database Dynamic secret created successfully (TTL: %d seconds)\n", ttl);
        
        // 임시 객체 정리
        json_object_put(new_secret);
        return 0;
    } else {
        fprintf(stderr, "❌ Failed to refresh Database Dynamic secret\n");
        return -1;
    }
}

// Database Dynamic 시크릿 가져오기 (캐시 확인)
int vault_get_db_dynamic_secret(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data || !client->config || !client->config->secret_database_dynamic.enabled) {
        return -1;
    }
    
    // 캐시가 없거나 오래된 경우 갱신
    if (!client->cached_db_dynamic_secret || vault_is_db_dynamic_secret_stale(client)) {
        printf("🔄 Database Dynamic cache is stale, refreshing...\n");
        if (vault_refresh_db_dynamic_secret(client) != 0) {
            return -1;
        }
    }
    
    // 캐시된 데이터 반환
    if (client->cached_db_dynamic_secret) {
        *secret_data = json_object_get(client->cached_db_dynamic_secret);
        return 0;
    }
    
    return -1;
}

// Database Dynamic 시크릿이 오래되었는지 확인
int vault_is_db_dynamic_secret_stale(vault_client_t *client) {
    if (!client || !client->config || !client->cached_db_dynamic_secret) {
        return 1;  // 캐시가 없으면 오래된 것으로 간주
    }
    
    // lease 상태 확인
    time_t expire_time;
    int ttl;
    if (vault_check_lease_status(client, client->lease_id, &expire_time, &ttl) == 0) {
        // Database Dynamic Secret은 TTL이 거의 만료될 때만 갱신 (10초 이하)
        int renewal_threshold = 10;  // 10초 이하일 때 갱신
        return (ttl <= renewal_threshold);
    }
    
    // lease 상태 확인 실패 시 기본 갱신 간격 사용
    time_t now = time(NULL);
    time_t elapsed = now - client->db_dynamic_last_refresh;
    int refresh_interval = client->config->secret_kv.refresh_interval; // KV와 동일한 간격 사용
    
    return (elapsed >= refresh_interval);
}

// Lease 상태 확인
int vault_check_lease_status(vault_client_t *client, const char *lease_id, time_t *expire_time, int *ttl) {
    if (!client || !lease_id || !expire_time || !ttl) {
        return -1;
    }
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(client->curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(client->curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(client->curl, CURLOPT_HTTPGET, 1L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/sys/leases/lookup", client->vault_url);
    curl_easy_setopt(client->curl, CURLOPT_URL, url);
    
    // Authorization 헤더 설정
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, auth_header);
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(client->curl, CURLOPT_HTTPHEADER, headers);
    
    // POST 데이터 설정
    char post_data[1024];
    snprintf(post_data, sizeof(post_data), "{\"lease_id\":\"%s\"}", lease_id);
    curl_easy_setopt(client->curl, CURLOPT_POSTFIELDS, post_data);
    curl_easy_setopt(client->curl, CURLOPT_POSTFIELDSIZE, strlen(post_data));
    curl_easy_setopt(client->curl, CURLOPT_CUSTOMREQUEST, "POST");
    
    // 요청 실행
    CURLcode res = curl_easy_perform(client->curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Lease status check failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        return -1;
    }
    
    // 응답 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse lease status response\n");
        free(response.data);
        return -1;
    }
    
    // TTL 추출
    json_object *data, *ttl_obj;
    if (json_object_object_get_ex(json_response, "data", &data) &&
        json_object_object_get_ex(data, "ttl", &ttl_obj)) {
        
        *ttl = json_object_get_int(ttl_obj);
        
        // expire_time 계산
        *expire_time = time(NULL) + *ttl;
        
        json_object_put(json_response);
        free(response.data);
        return 0;
    }
    json_object_put(json_response);
    free(response.data);
    return -1;
}

// Database Dynamic 시크릿 직접 가져오기 (JSON 구조가 다름)
int vault_get_db_dynamic_secret_direct(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data) return -1;
    
    // HTTP 요청 설정 (Database Dynamic Secret은 GET 요청)
    struct http_response response = {0};
    curl_easy_setopt(client->curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(client->curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(client->curl, CURLOPT_HTTPGET, 1L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/%s", client->vault_url, client->db_dynamic_path);
    curl_easy_setopt(client->curl, CURLOPT_URL, url);
    
    // Authorization 헤더 설정
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, auth_header);
    curl_easy_setopt(client->curl, CURLOPT_HTTPHEADER, headers);
    
    // 요청 실행
    CURLcode res = curl_easy_perform(client->curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Database Dynamic secret request failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        return -1;
    }
    
    // HTTP 상태 코드 확인
    long http_code;
    curl_easy_getinfo(client->curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    // 응답 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse Database Dynamic secret response\n");
        printf("Raw response: %s\n", response.data);
        free(response.data);
        return -1;
    }
    
    // 오류 확인
    json_object *errors;
    if (json_object_object_get_ex(json_response, "errors", &errors)) {
        printf("🔍 Debug: Vault returned errors:\n");
        printf("   %s\n", json_object_to_json_string(errors));
    }
    
    if (http_code != 200) {
        fprintf(stderr, "Database Dynamic secret request failed with HTTP %ld\n", http_code);
        printf("Response: %s\n", response.data);
        json_object_put(json_response);
        free(response.data);
        return -1;
    }
    
    // Database Dynamic 시크릿은 전체 응답을 반환 (KV와 달리 data.data 구조가 아님)
    *secret_data = json_object_get(json_response);
    json_object_get(*secret_data); // 참조 카운트 증가
    
    printf("Database Dynamic secret retrieved successfully\n");
    
    json_object_put(json_response);
    free(response.data);
    return 0;
}


// KV 시크릿 직접 가져오기 (전체 응답 반환)
int vault_get_kv_secret_direct(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data) return -1;
    
    // 새로운 CURL 핸들 생성
    CURL *curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "Failed to initialize CURL for KV secret\n");
        return -1;
    }
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, client->config->http_timeout);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    
    // URL 설정
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/%s", client->vault_url, client->kv_path);
    curl_easy_setopt(curl, CURLOPT_URL, url);
    
    // Authorization 헤더 설정
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, auth_header);

    // 네임스페이스 헤더 추가
    if (client->config && client->config->vault_namespace[0]) {
    char ns_header[256];
    snprintf(ns_header, sizeof(ns_header), "X-Vault-Namespace: %s", 
            client->config->vault_namespace);
    headers = curl_slist_append(headers, ns_header);
    }


    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    // 요청 실행
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "KV secret request failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // HTTP 상태 코드 확인
    long http_code;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

     // 412 에러 처리
    if (http_code == 412) {
        printf("⚠️ ETag cache expired (HTTP 412), retrying with fresh connection...\n");
        free(response.data);
        curl_easy_cleanup(curl);
        return vault_get_kv_secret_direct(client, secret_data);  // 재시도
    }

    if (http_code != 200) {
        fprintf(stderr, "KV secret request failed with HTTP %ld\n", http_code);
        printf("Response: %s\n", response.data);
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 응답 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse KV secret response\n");
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 오류 확인
    json_object *errors;
    if (json_object_object_get_ex(json_response, "errors", &errors)) {
        printf("🔍 Debug: Vault returned errors:\n");
        printf("   %s\n", json_object_to_json_string(errors));
        json_object_put(json_response);
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 전체 응답 반환 (메타데이터 포함)
    *secret_data = json_object_get(json_response);
    json_object_get(*secret_data); // 참조 카운트 증가
    
    printf("KV secret retrieved successfully\n");
    
    json_object_put(json_response);
    free(response.data);
    curl_easy_cleanup(curl);
    return 0;
}

// Database Dynamic 캐시 정리
void vault_cleanup_db_dynamic_cache(vault_client_t *client) {
    if (client && client->cached_db_dynamic_secret) {
        json_object_put(client->cached_db_dynamic_secret);
        client->cached_db_dynamic_secret = NULL;
        client->db_dynamic_last_refresh = 0;
        client->lease_id[0] = '\0';
        client->lease_expiry = 0;
    }
}

// Database Static 시크릿 갱신
int vault_refresh_db_static_secret(vault_client_t *client) {
    if (!client || !client->config || !client->config->secret_database_static.enabled) {
        return -1;
    }
    
    if (!client->db_static_path[0]) {
        fprintf(stderr, "Database Static path not configured\n");
        return -1;
    }
    
    printf("🔄 Refreshing Database Static secret from path: %s\n", client->db_static_path);
    
    // 새로운 시크릿 가져오기
    json_object *new_secret = NULL;
    int result = vault_get_db_static_secret_direct(client, &new_secret);
    
    if (result == 0 && new_secret) {
        // 기존 캐시 정리
        vault_cleanup_db_static_cache(client);
        
        // 캐시 업데이트
        client->cached_db_static_secret = json_object_get(new_secret);
        client->db_static_last_refresh = time(NULL);
        
        printf("✅ Database Static secret updated\n");
        
        json_object_put(new_secret);
        return 0;
    } else {
        fprintf(stderr, "❌ Failed to refresh Database Static secret\n");
        return -1;
    }
}

// Database Static 시크릿 가져오기 (캐시 확인)
int vault_get_db_static_secret(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data) {
        return -1;
    }
    
    // 캐시가 오래되었는지 확인
    if (vault_is_db_static_secret_stale(client)) {
        printf("🔄 Database Static cache is stale, refreshing...\n");
        if (vault_refresh_db_static_secret(client) != 0) {
            return -1;
        }
    }
    
    // 캐시된 시크릿 반환
    if (client->cached_db_static_secret) {
        *secret_data = json_object_get(client->cached_db_static_secret);
        return 0;
    }
    
    return -1;
}

// Database Static 시크릿 직접 가져오기 (HTTP 요청)
int vault_get_db_static_secret_direct(vault_client_t *client, json_object **secret_data) {
    if (!client || !secret_data) {
        return -1;
    }
    
    // 별도의 CURL 핸들 생성
    CURL *curl = curl_easy_init();
    if (!curl) {
        fprintf(stderr, "Failed to initialize CURL for Database Static secret\n");
        return -1;
    }
    
    // URL 구성
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/%s", client->vault_url, client->db_static_path);
    
    // HTTP 요청 설정
    struct http_response response = {0};
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
    curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, client->config->http_timeout);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    
    // 헤더 설정
    struct curl_slist *headers = NULL;
    char auth_header[1024];
    snprintf(auth_header, sizeof(auth_header), "X-Vault-Token: %s", client->token);
    headers = curl_slist_append(headers, auth_header);
    
    if (client->config->vault_namespace[0]) {
        char ns_header[256];
        snprintf(ns_header, sizeof(ns_header), "X-Vault-Namespace: %s", client->config->vault_namespace);
        headers = curl_slist_append(headers, ns_header);
    }
    
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    // HTTP 요청 실행
    CURLcode res = curl_easy_perform(curl);
    long http_code;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        fprintf(stderr, "Database Static secret request failed: %s\n", curl_easy_strerror(res));
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // JSON 파싱
    json_object *json_response = json_tokener_parse(response.data);
    if (!json_response) {
        fprintf(stderr, "Failed to parse Database Static secret response\n");
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    // 오류 확인
    json_object *errors;
    if (json_object_object_get_ex(json_response, "errors", &errors)) {
        printf("🔍 Debug: Vault returned errors:\n");
        printf("   %s\n", json_object_to_json_string(errors));
    }
    
    if (http_code != 200) {
        fprintf(stderr, "Database Static secret request failed with HTTP %ld\n", http_code);
        printf("Response: %s\n", response.data);
        json_object_put(json_response);
        free(response.data);
        curl_easy_cleanup(curl);
        return -1;
    }
    
    printf("Database Static secret retrieved successfully\n");
    
    // data 섹션만 반환
    json_object *data;
    if (json_object_object_get_ex(json_response, "data", &data)) {
        *secret_data = json_object_get(data);
    } else {
        *secret_data = json_object_get(json_response);
    }
    
    json_object_put(json_response);
    free(response.data);
    curl_easy_cleanup(curl);
    return 0;
}

// Database Static 시크릿이 오래되었는지 확인
int vault_is_db_static_secret_stale(vault_client_t *client) {
    if (!client || !client->cached_db_static_secret) {
        return 1; // 캐시가 없으면 stale
    }
    
    time_t now = time(NULL);
    time_t elapsed = now - client->db_static_last_refresh;
    
    // 5분마다 갱신 (Database Static은 자주 변경되지 않음)
    return (elapsed >= 300);
}

// Database Static 캐시 정리
void vault_cleanup_db_static_cache(vault_client_t *client) {
    if (client && client->cached_db_static_secret) {
        json_object_put(client->cached_db_static_secret);
        client->cached_db_static_secret = NULL;
        client->db_static_last_refresh = 0;
    }
}