#ifndef VAULT_CLIENT_HPP
#define VAULT_CLIENT_HPP

#include "Config.hpp"
#include "HttpClient.hpp"
#include "json.hpp"
#include <string>
#include <memory>
#include <mutex>
#include <atomic>
#include <chrono>
#include <optional>

/**
 * Vault 클라이언트 클래스
 * AppRole 인증, 토큰 관리, 시크릿 조회 기능 제공
 */
class VaultClient {
public:
    /**
     * 생성자
     * @param config 애플리케이션 설정
     */
    explicit VaultClient(const AppConfig& config);
    
    /**
     * 소멸자
     */
    ~VaultClient() = default;
    
    // 복사 생성자 및 대입 연산자 비활성화
    VaultClient(const VaultClient&) = delete;
    VaultClient& operator=(const VaultClient&) = delete;
    
    /**
     * AppRole 로그인
     * @param role_id AppRole Role ID
     * @param secret_id AppRole Secret ID
     * @return 성공 시 true, 실패 시 false
     */
    bool login(const std::string& role_id, const std::string& secret_id);
    
    /**
     * 토큰 갱신
     * @return 성공 시 true, 실패 시 false
     */
    bool renew_token();
    
    /**
     * 토큰 유효성 확인
     * @return 유효한 토큰이면 true, 아니면 false
     */
    bool is_token_valid() const;
    
    /**
     * 토큰 상태 출력
     */
    void print_token_status() const;
    
    /**
     * KV 시크릿 가져오기 (캐시 확인)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_kv_secret(nlohmann::json& secret_data);
    
    /**
     * Database Dynamic 시크릿 가져오기 (캐시 확인)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_db_dynamic_secret(nlohmann::json& secret_data);
    
    /**
     * Database Static 시크릿 가져오기 (캐시 확인)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_db_static_secret(nlohmann::json& secret_data);
    
    /**
     * KV 시크릿 갱신
     * @return 성공 시 true, 실패 시 false
     */
    bool refresh_kv_secret();
    
    /**
     * Database Dynamic 시크릿 갱신
     * @return 성공 시 true, 실패 시 false
     */
    bool refresh_db_dynamic_secret();
    
    /**
     * Database Static 시크릿 갱신
     * @return 성공 시 true, 실패 시 false
     */
    bool refresh_db_static_secret();
    
    /**
     * KV 버전 정보 가져오기
     * @return KV 시크릿 버전
     */
    int get_kv_version() const;
    
    /**
     * Database Dynamic TTL 확인
     * @param ttl TTL 값 (초)
     * @return 성공 시 true, 실패 시 false
     */
    bool get_db_dynamic_ttl(int& ttl) const;

private:
    const AppConfig& config_;
    std::unique_ptr<HttpClient> http_client_;
    
    // 토큰 관리
    std::string token_;
    std::chrono::system_clock::time_point token_issued_;
    std::chrono::system_clock::time_point token_expiry_;
    mutable std::mutex token_mutex_;
    
    // KV 시크릿 캐시
    std::shared_ptr<nlohmann::json> cached_kv_secret_;
    std::chrono::system_clock::time_point kv_last_refresh_;
    std::string kv_path_;
    int kv_version_;
    mutable std::mutex kv_mutex_;
    
    // Database Dynamic 시크릿 캐시
    std::shared_ptr<nlohmann::json> cached_db_dynamic_secret_;
    std::chrono::system_clock::time_point db_dynamic_last_refresh_;
    std::string db_dynamic_path_;
    std::string lease_id_;
    std::chrono::system_clock::time_point lease_expiry_;
    mutable std::mutex db_dynamic_mutex_;
    
    // Database Static 시크릿 캐시
    std::shared_ptr<nlohmann::json> cached_db_static_secret_;
    std::chrono::system_clock::time_point db_static_last_refresh_;
    std::string db_static_path_;
    mutable std::mutex db_static_mutex_;
    
    /**
     * 시크릿 가져오기 (공통)
     * @param path 시크릿 경로
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_secret(const std::string& path, nlohmann::json& secret_data);
    
    /**
     * KV 시크릿 직접 가져오기 (캐시 무시)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_kv_secret_direct(nlohmann::json& secret_data);
    
    /**
     * Database Dynamic 시크릿 직접 가져오기 (캐시 무시)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_db_dynamic_secret_direct(nlohmann::json& secret_data);
    
    /**
     * Database Static 시크릿 직접 가져오기 (캐시 무시)
     * @param secret_data 반환할 시크릿 데이터
     * @return 성공 시 true, 실패 시 false
     */
    bool get_db_static_secret_direct(nlohmann::json& secret_data);
    
    /**
     * KV 시크릿이 오래되었는지 확인
     * @return 오래된 시크릿이면 true, 아니면 false
     */
    bool is_kv_secret_stale() const;
    
    /**
     * Database Dynamic 시크릿이 오래되었는지 확인
     * @return 오래된 시크릿이면 true, 아니면 false
     */
    bool is_db_dynamic_secret_stale() const;
    
    /**
     * Database Static 시크릿이 오래되었는지 확인
     * @return 오래된 시크릿이면 true, 아니면 false
     */
    bool is_db_static_secret_stale() const;
    
    /**
     * Lease 상태 확인
     * @param lease_id 확인할 lease ID
     * @param expire_time 만료 시간
     * @param ttl TTL 값 (초)
     * @return 성공 시 true, 실패 시 false
     */
    bool check_lease_status(const std::string& lease_id, 
                           std::chrono::system_clock::time_point& expire_time, 
                           int& ttl) const;
    
    /**
     * Authorization 헤더 생성
     * @return Authorization 헤더 맵
     */
    std::map<std::string, std::string> get_auth_headers() const;
    
    /**
     * Namespace 헤더 생성 (필요한 경우)
     * @return Namespace 헤더 맵
     */
    std::map<std::string, std::string> get_namespace_headers() const;
    
    /**
     * 현재 시간 가져오기
     * @return 현재 시간
     */
    static std::chrono::system_clock::time_point now();
    
    /**
     * 시간 차이 계산 (초)
     * @param start 시작 시간
     * @param end 종료 시간
     * @return 시간 차이 (초)
     */
    static long get_duration_seconds(const std::chrono::system_clock::time_point& start,
                                    const std::chrono::system_clock::time_point& end);
};

#endif // VAULT_CLIENT_HPP
