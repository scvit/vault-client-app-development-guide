#include "VaultClient.hpp"
#include <iostream>
#include <sstream>
#include <iomanip>

VaultClient::VaultClient(const AppConfig& config) 
    : config_(config), kv_version_(-1) {
    
    // HTTP 클라이언트 초기화
    http_client_ = std::make_unique<HttpClient>(config.http_timeout);
    
    // 경로 설정 (Entity 기반)
    if (config.secret_kv.enabled && !config.secret_kv.kv_path.empty()) {
        kv_path_ = config.entity + "-kv/data/" + config.secret_kv.kv_path;
    }
    
    if (config.secret_database_dynamic.enabled && !config.secret_database_dynamic.role_id.empty()) {
        db_dynamic_path_ = config.entity + "-database/creds/" + config.secret_database_dynamic.role_id;
    }
    
    if (config.secret_database_static.enabled && !config.secret_database_static.role_id.empty()) {
        db_static_path_ = config.entity + "-database/static-creds/" + config.secret_database_static.role_id;
    }
}

bool VaultClient::login(const std::string& role_id, const std::string& secret_id) {
    std::lock_guard<std::mutex> lock(token_mutex_);
    
    // JSON 요청 생성
    nlohmann::json request;
    request["role_id"] = role_id;
    request["secret_id"] = secret_id;
    
    std::string json_string = request.dump();
    
    // URL 구성
    std::string url = config_.vault_url + "/v1/auth/approle/login";
    
    // HTTP 요청
    auto response = http_client_->post_json(url, json_string);
    
    if (!response.is_success()) {
        std::cerr << "Login request failed with status: " << response.status_code << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // 토큰 추출
        if (json_response.contains("auth") && json_response["auth"].contains("client_token")) {
            token_ = json_response["auth"]["client_token"];
            token_issued_ = now();
            
            // 토큰 만료 시간 설정
            if (json_response["auth"].contains("lease_duration")) {
                int ttl_seconds = json_response["auth"]["lease_duration"];
                token_expiry_ = token_issued_ + std::chrono::seconds(ttl_seconds);
                std::cout << "Token TTL from Vault: " << ttl_seconds << " seconds" << std::endl;
            } else {
                // TTL 정보가 없으면 기본값 사용 (1시간)
                token_expiry_ = token_issued_ + std::chrono::seconds(3600);
                std::cout << "Warning: No TTL info from Vault, using default 1 hour" << std::endl;
            }
            
            long remaining = get_duration_seconds(now(), token_expiry_);
            std::cout << "Login successful. Token expires in " << remaining << " seconds" << std::endl;
            return true;
        } else {
            std::cerr << "Failed to extract token from response" << std::endl;
            return false;
        }
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse login response: " << e.what() << std::endl;
        return false;
    }
}

bool VaultClient::renew_token() {
    std::lock_guard<std::mutex> lock(token_mutex_);
    
    if (token_.empty()) {
        return false;
    }
    
    // URL 구성
    std::string url = config_.vault_url + "/v1/auth/token/renew-self";
    
    // 헤더 설정
    auto headers = get_auth_headers();
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // HTTP 요청
    auto response = http_client_->post(url, "", headers);
    
    if (!response.is_success()) {
        std::cerr << "Token renewal failed with status: " << response.status_code << std::endl;
        std::cout << "Response: " << response.data << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        if (json_response.contains("auth") && json_response["auth"].contains("lease_duration")) {
            int lease_seconds = json_response["auth"]["lease_duration"];
            auto now_time = now();
            token_issued_ = now_time;
            token_expiry_ = now_time + std::chrono::seconds(lease_seconds);
            
            long remaining = get_duration_seconds(now_time, token_expiry_);
            std::cout << "Token renewed successfully. New expiry: " << remaining << " seconds" << std::endl;
            return true;
        } else {
            std::cout << "Warning: No lease_duration in renewal response" << std::endl;
            std::cout << "Renewal response: " << response.data << std::endl;
            return false;
        }
    } catch (const nlohmann::json::parse_error& e) {
        std::cout << "Warning: Failed to parse renewal response: " << e.what() << std::endl;
        std::cout << "Renewal response: " << response.data << std::endl;
        return false;
    }
}

bool VaultClient::is_token_valid() const {
    std::lock_guard<std::mutex> lock(token_mutex_);
    
    if (token_.empty()) {
        return false;
    }
    
    auto now_time = now();
    auto total_ttl = get_duration_seconds(token_issued_, token_expiry_);
    auto elapsed = get_duration_seconds(token_issued_, now_time);
    auto renewal_point = total_ttl * 4 / 5;  // 4/5 지점에서 갱신 필요
    
    return (elapsed < renewal_point);
}

void VaultClient::print_token_status() const {
    std::lock_guard<std::mutex> lock(token_mutex_);
    
    if (token_.empty()) {
        std::cout << "❌ No token available!" << std::endl;
        return;
    }
    
    auto now_time = now();
    auto remaining = get_duration_seconds(now_time, token_expiry_);
    
    if (remaining > 0) {
        std::cout << "Token status: " << remaining << " seconds remaining (expires in " 
                  << (remaining / 60) << " minutes)" << std::endl;
        
        // 갱신 권장 시점 계산 (4/5 지점 기준)
        auto total_ttl = get_duration_seconds(token_issued_, token_expiry_);
        auto elapsed = get_duration_seconds(token_issued_, now_time);
        auto renewal_point = total_ttl * 4 / 5;  // 4/5 지점
        auto urgent_point = total_ttl * 9 / 10;  // 9/10 지점
        
        if (elapsed >= urgent_point) {
            std::cout << "⚠️  URGENT: Token should be renewed soon (at " 
                      << (elapsed * 100) / total_ttl << "% of TTL)" << std::endl;
        } else if (elapsed >= renewal_point) {
            std::cout << "🔄 Token renewal recommended (at " 
                      << (elapsed * 100) / total_ttl << "% of TTL)" << std::endl;
        } else {
            std::cout << "✅ Token is healthy (at " 
                      << (elapsed * 100) / total_ttl << "% of TTL)" << std::endl;
        }
    } else {
        std::cout << "❌ Token has expired!" << std::endl;
    }
}

bool VaultClient::get_kv_secret(nlohmann::json& secret_data) {
    std::lock_guard<std::mutex> lock(kv_mutex_);
    
    if (!config_.secret_kv.enabled) {
        return false;
    }
    
    // 캐시가 없거나 오래된 경우 갱신
    if (!cached_kv_secret_ || is_kv_secret_stale()) {
        std::cout << "🔄 KV cache is stale, refreshing..." << std::endl;
        if (!refresh_kv_secret()) {
            return false;
        }
    }
    
    // 캐시된 데이터 반환
    if (cached_kv_secret_) {
        secret_data = *cached_kv_secret_;
        return true;
    }
    
    return false;
}

bool VaultClient::get_db_dynamic_secret(nlohmann::json& secret_data) {
    std::lock_guard<std::mutex> lock(db_dynamic_mutex_);
    
    if (!config_.secret_database_dynamic.enabled) {
        return false;
    }
    
    // 캐시가 없거나 오래된 경우 갱신
    if (!cached_db_dynamic_secret_ || is_db_dynamic_secret_stale()) {
        std::cout << "🔄 Database Dynamic cache is stale, refreshing..." << std::endl;
        if (!refresh_db_dynamic_secret()) {
            return false;
        }
    }
    
    // 캐시된 데이터 반환
    if (cached_db_dynamic_secret_) {
        secret_data = *cached_db_dynamic_secret_;
        return true;
    }
    
    return false;
}

bool VaultClient::get_db_static_secret(nlohmann::json& secret_data) {
    std::lock_guard<std::mutex> lock(db_static_mutex_);
    
    if (!config_.secret_database_static.enabled) {
        return false;
    }
    
    // 캐시가 오래되었는지 확인
    if (is_db_static_secret_stale()) {
        std::cout << "🔄 Database Static cache is stale, refreshing..." << std::endl;
        if (!refresh_db_static_secret()) {
            return false;
        }
    }
    
    // 캐시된 시크릿 반환
    if (cached_db_static_secret_) {
        secret_data = *cached_db_static_secret_;
        return true;
    }
    
    return false;
}

bool VaultClient::refresh_kv_secret() {
    std::lock_guard<std::mutex> lock(kv_mutex_);
    
    if (!config_.secret_kv.enabled || kv_path_.empty()) {
        return false;
    }
    
    std::cout << "🔄 Refreshing KV secret from path: " << kv_path_ << std::endl;
    
    // 새로운 시크릿 가져오기
    nlohmann::json new_secret;
    if (!get_kv_secret_direct(new_secret)) {
        std::cerr << "❌ Failed to refresh KV secret" << std::endl;
        return false;
    }
    
    // 버전 정보 추출
    int new_version = -1;
    if (new_secret.contains("data") && 
        new_secret["data"].contains("metadata") && 
        new_secret["data"]["metadata"].contains("version")) {
        new_version = new_secret["data"]["metadata"]["version"];
    }
    
    // 버전이 다르거나 캐시가 없는 경우에만 업데이트
    if (new_version != kv_version_) {
        // 캐시 업데이트
        cached_kv_secret_ = std::make_shared<nlohmann::json>(new_secret);
        kv_last_refresh_ = now();
        kv_version_ = new_version;
        
        std::cout << "✅ KV secret updated (version: " << new_version << ")" << std::endl;
    } else {
        std::cout << "✅ KV secret unchanged (version: " << new_version << ")" << std::endl;
        kv_last_refresh_ = now();  // 마지막 확인 시간 업데이트
    }
    
    return true;
}

bool VaultClient::refresh_db_dynamic_secret() {
    std::lock_guard<std::mutex> lock(db_dynamic_mutex_);
    
    if (!config_.secret_database_dynamic.enabled || db_dynamic_path_.empty()) {
        return false;
    }
    
    std::cout << "🔄 Refreshing Database Dynamic secret from path: " << db_dynamic_path_ << std::endl;
    
    // 기존 캐시가 있는 경우 TTL 확인
    if (cached_db_dynamic_secret_ && !lease_id_.empty()) {
        std::chrono::system_clock::time_point expire_time;
        int ttl;
        if (check_lease_status(lease_id_, expire_time, ttl)) {
            // TTL이 충분히 남아있으면 갱신하지 않음
            if (ttl > 10) {  // 10초 이상 남아있으면 갱신하지 않음
                std::cout << "✅ Database Dynamic secret is still valid (TTL: " << ttl << " seconds)" << std::endl;
                db_dynamic_last_refresh_ = now();
                return true;
            } else {
                std::cout << "⚠️ Database Dynamic secret expiring soon (TTL: " << ttl << " seconds), creating new credentials" << std::endl;
            }
        }
    }
    
    // 새로운 Database Dynamic 시크릿 생성
    nlohmann::json new_secret;
    if (!get_db_dynamic_secret_direct(new_secret)) {
        std::cerr << "❌ Failed to refresh Database Dynamic secret" << std::endl;
        return false;
    }
    
    // lease_id 추출
    if (new_secret.contains("lease_id")) {
        lease_id_ = new_secret["lease_id"];
    }
    
    // 캐시 업데이트
    cached_db_dynamic_secret_ = std::make_shared<nlohmann::json>(new_secret);
    db_dynamic_last_refresh_ = now();
    
    // lease 만료 시간 확인
    if (!lease_id_.empty()) {
        std::chrono::system_clock::time_point expire_time;
        int ttl = 0;
        if (check_lease_status(lease_id_, expire_time, ttl)) {
            lease_expiry_ = expire_time;
            std::cout << "✅ Database Dynamic secret created successfully (TTL: " << ttl << " seconds)" << std::endl;
        }
    }
    
    return true;
}

bool VaultClient::refresh_db_static_secret() {
    std::lock_guard<std::mutex> lock(db_static_mutex_);
    
    if (!config_.secret_database_static.enabled || db_static_path_.empty()) {
        return false;
    }
    
    std::cout << "🔄 Refreshing Database Static secret from path: " << db_static_path_ << std::endl;
    
    // 새로운 시크릿 가져오기
    nlohmann::json new_secret;
    if (!get_db_static_secret_direct(new_secret)) {
        std::cerr << "❌ Failed to refresh Database Static secret" << std::endl;
        return false;
    }
    
    // 캐시 업데이트
    cached_db_static_secret_ = std::make_shared<nlohmann::json>(new_secret);
    db_static_last_refresh_ = now();
    
    std::cout << "✅ Database Static secret updated" << std::endl;
    return true;
}

int VaultClient::get_kv_version() const {
    std::lock_guard<std::mutex> lock(kv_mutex_);
    return kv_version_;
}

bool VaultClient::get_db_dynamic_ttl(int& ttl) const {
    std::lock_guard<std::mutex> lock(db_dynamic_mutex_);
    
    if (lease_id_.empty()) {
        return false;
    }
    
    std::chrono::system_clock::time_point expire_time;
    return check_lease_status(lease_id_, expire_time, ttl);
}

bool VaultClient::get_secret(const std::string& path, nlohmann::json& secret_data) {
    // URL 구성
    std::string url = config_.vault_url + "/v1/" + path;
    
    // 헤더 설정
    auto headers = get_auth_headers();
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // HTTP 요청
    auto response = http_client_->get(url, headers);
    
    if (!response.is_success()) {
        std::cerr << "Secret request failed with status: " << response.status_code << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // 오류 확인
        if (json_response.contains("errors")) {
            std::cout << "🔍 Debug: Vault returned errors:" << std::endl;
            std::cout << "   " << json_response["errors"].dump() << std::endl;
        }
        
        // 시크릿 데이터 추출
        if (json_response.contains("data") && json_response["data"].contains("data")) {
            secret_data = json_response["data"]["data"];
            std::cout << "Secret retrieved successfully" << std::endl;
            return true;
        } else {
            std::cerr << "Failed to extract secret data" << std::endl;
            return false;
        }
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse secret response: " << e.what() << std::endl;
        return false;
    }
}

bool VaultClient::get_kv_secret_direct(nlohmann::json& secret_data) {
    // URL 구성
    std::string url = config_.vault_url + "/v1/" + kv_path_;
    
    // 헤더 설정
    auto headers = get_auth_headers();
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // HTTP 요청
    auto response = http_client_->get(url, headers);
    
    if (!response.is_success()) {
        std::cerr << "KV secret request failed with status: " << response.status_code << std::endl;
        std::cout << "Response: " << response.data << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // 오류 확인
        if (json_response.contains("errors")) {
            std::cout << "🔍 Debug: Vault returned errors:" << std::endl;
            std::cout << "   " << json_response["errors"].dump() << std::endl;
        }
        
        // 전체 응답 반환 (메타데이터 포함)
        secret_data = json_response;
        std::cout << "KV secret retrieved successfully" << std::endl;
        return true;
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse KV secret response: " << e.what() << std::endl;
        return false;
    }
}

bool VaultClient::get_db_dynamic_secret_direct(nlohmann::json& secret_data) {
    // URL 구성
    std::string url = config_.vault_url + "/v1/" + db_dynamic_path_;
    
    // 헤더 설정
    auto headers = get_auth_headers();
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // HTTP 요청
    auto response = http_client_->get(url, headers);
    
    if (!response.is_success()) {
        std::cerr << "Database Dynamic secret request failed with status: " << response.status_code << std::endl;
        std::cout << "Response: " << response.data << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // 오류 확인
        if (json_response.contains("errors")) {
            std::cout << "🔍 Debug: Vault returned errors:" << std::endl;
            std::cout << "   " << json_response["errors"].dump() << std::endl;
        }
        
        // Database Dynamic 시크릿은 전체 응답을 반환 (KV와 달리 data.data 구조가 아님)
        secret_data = json_response;
        std::cout << "Database Dynamic secret retrieved successfully" << std::endl;
        return true;
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse Database Dynamic secret response: " << e.what() << std::endl;
        return false;
    }
}

bool VaultClient::get_db_static_secret_direct(nlohmann::json& secret_data) {
    // URL 구성
    std::string url = config_.vault_url + "/v1/" + db_static_path_;
    
    // 헤더 설정
    auto headers = get_auth_headers();
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // HTTP 요청
    auto response = http_client_->get(url, headers);
    
    if (!response.is_success()) {
        std::cerr << "Database Static secret request failed with status: " << response.status_code << std::endl;
        std::cout << "Response: " << response.data << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // 오류 확인
        if (json_response.contains("errors")) {
            std::cout << "🔍 Debug: Vault returned errors:" << std::endl;
            std::cout << "   " << json_response["errors"].dump() << std::endl;
        }
        
        std::cout << "Database Static secret retrieved successfully" << std::endl;
        
        // data 섹션만 반환
        if (json_response.contains("data")) {
            secret_data = json_response["data"];
        } else {
            secret_data = json_response;
        }
        
        return true;
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse Database Static secret response: " << e.what() << std::endl;
        return false;
    }
}

bool VaultClient::is_kv_secret_stale() const {
    // 캐시가 없으면 항상 갱신 필요
    if (!cached_kv_secret_) {
        return true;
    }
    
    // 버전 기반 갱신: 항상 최신 버전 확인
    // KV v2는 버전 정보를 제공하므로 시간 기반이 아닌 버전 기반으로 갱신
    return true;  // 항상 버전 확인을 위해 갱신 시도
}

bool VaultClient::is_db_dynamic_secret_stale() const {
    if (!cached_db_dynamic_secret_) {
        return true;  // 캐시가 없으면 오래된 것으로 간주
    }
    
    // lease 상태 확인
    if (!lease_id_.empty()) {
        std::chrono::system_clock::time_point expire_time;
        int ttl;
        if (check_lease_status(lease_id_, expire_time, ttl)) {
            // Database Dynamic Secret은 TTL이 거의 만료될 때만 갱신 (10초 이하)
            int renewal_threshold = 10;  // 10초 이하일 때 갱신
            return (ttl <= renewal_threshold);
        }
    }
    
    // lease 상태 확인 실패 시 기본 갱신 간격 사용
    auto now_time = now();
    auto elapsed = get_duration_seconds(db_dynamic_last_refresh_, now_time);
    int refresh_interval = config_.secret_kv.refresh_interval; // KV와 동일한 간격 사용
    
    return (elapsed >= refresh_interval);
}

bool VaultClient::is_db_static_secret_stale() const {
    if (!cached_db_static_secret_) {
        return true; // 캐시가 없으면 stale
    }
    
    auto now_time = now();
    auto elapsed = get_duration_seconds(db_static_last_refresh_, now_time);
    
    // 5분마다 갱신 (Database Static은 자주 변경되지 않음)
    return (elapsed >= 300);
}

bool VaultClient::check_lease_status(const std::string& lease_id, 
                                    std::chrono::system_clock::time_point& expire_time, 
                                    int& ttl) const {
    // URL 구성
    std::string url = config_.vault_url + "/v1/sys/leases/lookup";
    
    // 헤더 설정
    auto headers = get_auth_headers();
    headers["Content-Type"] = "application/json";
    auto ns_headers = get_namespace_headers();
    headers.insert(ns_headers.begin(), ns_headers.end());
    
    // POST 데이터 설정
    nlohmann::json post_data;
    post_data["lease_id"] = lease_id;
    std::string json_string = post_data.dump();
    
    // HTTP 요청
    auto response = http_client_->post(url, json_string, headers);
    
    if (!response.is_success()) {
        std::cerr << "Lease status check failed with status: " << response.status_code << std::endl;
        return false;
    }
    
    try {
        auto json_response = nlohmann::json::parse(response.data);
        
        // TTL 추출
        if (json_response.contains("data") && json_response["data"].contains("ttl")) {
            ttl = json_response["data"]["ttl"];
            
            // expire_time 계산
            expire_time = now() + std::chrono::seconds(ttl);
            
            return true;
        }
    } catch (const nlohmann::json::parse_error& e) {
        std::cerr << "Failed to parse lease status response: " << e.what() << std::endl;
    }
    
    return false;
}

std::map<std::string, std::string> VaultClient::get_auth_headers() const {
    std::lock_guard<std::mutex> lock(token_mutex_);
    return {{"X-Vault-Token", token_}};
}

std::map<std::string, std::string> VaultClient::get_namespace_headers() const {
    if (!config_.vault_namespace.empty()) {
        return {{"X-Vault-Namespace", config_.vault_namespace}};
    }
    return {};
}

std::chrono::system_clock::time_point VaultClient::now() {
    return std::chrono::system_clock::now();
}

long VaultClient::get_duration_seconds(const std::chrono::system_clock::time_point& start,
                                      const std::chrono::system_clock::time_point& end) {
    return std::chrono::duration_cast<std::chrono::seconds>(end - start).count();
}
