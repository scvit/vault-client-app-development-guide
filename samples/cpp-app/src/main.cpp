#include "Config.hpp"
#include "VaultClient.hpp"
#include <iostream>
#include <thread>
#include <atomic>
#include <chrono>
#include <csignal>
#include <memory>

// 전역 변수
std::unique_ptr<VaultClient> vault_client;
AppConfig app_config;
std::atomic<bool> should_exit{false};

// 시그널 처리
void signal_handler(int sig) {
    std::cout << "\nReceived signal " << sig << ". Shutting down..." << std::endl;
    should_exit = true;
    
    // 강제 종료를 위한 추가 시그널 설정
    if (sig == SIGINT) {
        std::signal(SIGINT, SIG_DFL); // 다음 Ctrl+C는 강제 종료
    }
}

// KV 시크릿 갱신 스레드
void kv_refresh_thread() {
    while (!should_exit) {
        // 설정된 간격만큼 대기
        int refresh_interval = app_config.secret_kv.refresh_interval;
        for (int i = 0; i < refresh_interval && !should_exit; i++) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        
        if (should_exit) break;
        
        // KV 시크릿 갱신
        if (app_config.secret_kv.enabled) {
            std::cout << "\n=== KV Secret Refresh ===" << std::endl;
            vault_client->refresh_kv_secret();
        }
    }
    
    std::cout << "KV refresh thread terminated" << std::endl;
}

// Database Dynamic 시크릿 갱신 스레드
void db_dynamic_refresh_thread() {
    while (!should_exit) {
        // 설정된 간격만큼 대기
        int refresh_interval = app_config.secret_kv.refresh_interval;
        for (int i = 0; i < refresh_interval && !should_exit; i++) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        
        if (should_exit) break;
        
        // Database Dynamic 시크릿 갱신
        if (app_config.secret_database_dynamic.enabled) {
            std::cout << "\n=== Database Dynamic Secret Refresh ===" << std::endl;
            vault_client->refresh_db_dynamic_secret();
        }
    }
    
    std::cout << "Database Dynamic refresh thread terminated" << std::endl;
}

// Database Static 시크릿 갱신 스레드
void db_static_refresh_thread() {
    while (!should_exit) {
        // 설정된 간격만큼 대기 (Database Static은 자주 변경되지 않으므로 더 긴 간격)
        int refresh_interval = app_config.secret_kv.refresh_interval * 2; // 2배 간격
        for (int i = 0; i < refresh_interval && !should_exit; i++) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        
        if (should_exit) break;
        
        // Database Static 시크릿 갱신
        if (app_config.secret_database_static.enabled) {
            std::cout << "\n=== Database Static Secret Refresh ===" << std::endl;
            vault_client->refresh_db_static_secret();
        }
    }
    
    std::cout << "Database Static refresh thread terminated" << std::endl;
}

// 토큰 갱신 스레드 (안전한 갱신 로직)
void token_renewal_thread() {
    while (!should_exit) {
        // 10초마다 토큰 상태 확인 (짧은 TTL에 대응)
        for (int i = 0; i < 10 && !should_exit; i++) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        
        if (should_exit) break;
        
        // 토큰 상태 출력
        std::cout << "\n=== Token Status Check ===" << std::endl;
        vault_client->print_token_status();
        
        // 갱신 필요 여부 확인 (4/5 지점에서 갱신)
        if (!vault_client->is_token_valid()) {
            std::cout << "🔄 Token renewal triggered" << std::endl;
            
            if (!vault_client->renew_token()) {
                std::cout << "❌ Token renewal failed. Attempting re-login..." << std::endl;
                if (!vault_client->login(app_config.vault_role_id, app_config.vault_secret_id)) {
                    std::cerr << "❌ Re-login failed. Exiting..." << std::endl;
                    should_exit = true;
                    break;
                } else {
                    std::cout << "✅ Re-login successful" << std::endl;
                    vault_client->print_token_status();
                }
            } else {
                std::cout << "✅ Token renewed successfully" << std::endl;
                vault_client->print_token_status();
            }
        } else {
            std::cout << "✅ Token is still healthy, no renewal needed" << std::endl;
        }
    }
    
    std::cout << "Token renewal thread terminated" << std::endl;
}

int main(int argc, char* argv[]) {
    // 시그널 처리 설정
    std::signal(SIGINT, signal_handler);
    std::signal(SIGTERM, signal_handler);
    
    std::cout << "=== Vault C++ Client Application ===" << std::endl;
    
    // 설정 파일 경로 결정
    std::string config_file = "config.ini";
    if (argc > 1) {
        config_file = argv[1];
    }
    
    // 설정 파일 로드
    std::cout << "Loading configuration from: " << config_file << std::endl;
    if (ConfigLoader::load_config(config_file, app_config) != 0) {
        std::cerr << "Failed to load configuration" << std::endl;
        return 1;
    }
    
    // 설정 출력
    ConfigLoader::print_config(app_config);
    
    // Vault 클라이언트 초기화
    try {
        vault_client = std::make_unique<VaultClient>(app_config);
    } catch (const std::exception& e) {
        std::cerr << "Failed to initialize Vault client: " << e.what() << std::endl;
        return 1;
    }
    
    // AppRole 로그인
    std::cout << "Logging in to Vault..." << std::endl;
    if (!vault_client->login(app_config.vault_role_id, app_config.vault_secret_id)) {
        std::cerr << "Login failed" << std::endl;
        return 1;
    }
    
    // 토큰 상태 출력
    vault_client->print_token_status();
    
    // 토큰 갱신 스레드 시작
    std::thread renewal_thread(token_renewal_thread);
    
    // KV 갱신 스레드 시작 (KV 엔진이 활성화된 경우)
    std::thread kv_refresh_thread_handle;
    if (app_config.secret_kv.enabled) {
        kv_refresh_thread_handle = std::thread(kv_refresh_thread);
        std::cout << "✅ KV refresh thread started (interval: " << app_config.secret_kv.refresh_interval << " seconds)" << std::endl;
    }
    
    // Database Dynamic 갱신 스레드 시작 (Database Dynamic 엔진이 활성화된 경우)
    std::thread db_dynamic_refresh_thread_handle;
    if (app_config.secret_database_dynamic.enabled) {
        db_dynamic_refresh_thread_handle = std::thread(db_dynamic_refresh_thread);
        std::cout << "✅ Database Dynamic refresh thread started (interval: " << app_config.secret_kv.refresh_interval << " seconds)" << std::endl;
    }
    
    // Database Static 갱신 스레드 시작 (Database Static 엔진이 활성화된 경우)
    std::thread db_static_refresh_thread_handle;
    if (app_config.secret_database_static.enabled) {
        db_static_refresh_thread_handle = std::thread(db_static_refresh_thread);
        std::cout << "✅ Database Static refresh thread started (interval: " << (app_config.secret_kv.refresh_interval * 2) << " seconds)" << std::endl;
    }
    
    // 메인 루프
    while (!should_exit) {
        std::cout << "\n=== Fetching Secret ===" << std::endl;
        
        // KV 시크릿 가져오기 (캐시 확인)
        if (app_config.secret_kv.enabled) {
            nlohmann::json kv_secret;
            if (vault_client->get_kv_secret(kv_secret)) {
                // data.data 부분만 추출하여 출력
                if (kv_secret.contains("data") && kv_secret["data"].contains("data")) {
                    std::cout << "📦 KV Secret Data (version: " << vault_client->get_kv_version() << "):" << std::endl;
                    std::cout << kv_secret["data"]["data"].dump() << std::endl;
                }
            } else {
                std::cerr << "Failed to retrieve KV secret" << std::endl;
            }
        }
        
        // Database Dynamic 시크릿 가져오기 (캐시 확인)
        if (app_config.secret_database_dynamic.enabled) {
            nlohmann::json db_dynamic_secret;
            if (vault_client->get_db_dynamic_secret(db_dynamic_secret)) {
                // TTL 정보 가져오기
                int ttl = 0;
                if (vault_client->get_db_dynamic_ttl(ttl)) {
                    std::cout << "🗄️ Database Dynamic Secret (TTL: " << ttl << " seconds):" << std::endl;
                } else {
                    std::cout << "🗄️ Database Dynamic Secret:" << std::endl;
                }
                
                // data 섹션에서 username과 password만 추출
                if (db_dynamic_secret.contains("data")) {
                    auto data = db_dynamic_secret["data"];
                    if (data.contains("username") && data.contains("password")) {
                        std::cout << "  username: " << data["username"] << std::endl;
                        std::cout << "  password: " << data["password"] << std::endl;
                    }
                }
            } else {
                std::cerr << "Failed to retrieve Database Dynamic secret" << std::endl;
            }
        }
        
        // Database Static 시크릿 가져오기 (캐시 확인)
        if (app_config.secret_database_static.enabled) {
            nlohmann::json db_static_secret;
            if (vault_client->get_db_static_secret(db_static_secret)) {
                // TTL 정보 추출
                int ttl = 0;
                if (db_static_secret.contains("ttl")) {
                    ttl = db_static_secret["ttl"];
                }
                
                if (ttl > 0) {
                    std::cout << "🔒 Database Static Secret (TTL: " << ttl << " seconds):" << std::endl;
                } else {
                    std::cout << "🔒 Database Static Secret:" << std::endl;
                }
                
                // username과 password 추출
                if (db_static_secret.contains("username") && db_static_secret.contains("password")) {
                    std::cout << "  username: " << db_static_secret["username"] << std::endl;
                    std::cout << "  password: " << db_static_secret["password"] << std::endl;
                }
            } else {
                std::cerr << "Failed to retrieve Database Static secret" << std::endl;
            }
        }
        
        // 토큰 상태 간단 출력
        std::cout << "\n--- Token Status ---" << std::endl;
        vault_client->print_token_status();
        
        // 10초 대기
        for (int i = 0; i < 10 && !should_exit; i++) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
    }
    
    // 정리
    std::cout << "Cleaning up..." << std::endl;
    
    // 스레드 종료 대기
    if (renewal_thread.joinable()) {
        renewal_thread.join();
    }
    
    if (kv_refresh_thread_handle.joinable()) {
        kv_refresh_thread_handle.join();
    }
    
    if (db_dynamic_refresh_thread_handle.joinable()) {
        db_dynamic_refresh_thread_handle.join();
    }
    
    if (db_static_refresh_thread_handle.joinable()) {
        db_static_refresh_thread_handle.join();
    }
    
    vault_client.reset();
    
    std::cout << "Application terminated" << std::endl;
    return 0;
}
