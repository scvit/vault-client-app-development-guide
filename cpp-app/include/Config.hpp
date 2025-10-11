#ifndef CONFIG_HPP
#define CONFIG_HPP

#include <string>
#include <optional>

/**
 * 애플리케이션 설정 구조체
 * C 버전과 동일한 설정을 C++ 스타일로 구현
 */
struct AppConfig {
    // Vault 기본 설정
    std::string vault_url;
    std::string vault_namespace;
    std::string vault_role_id;
    std::string vault_secret_id;
    std::string entity;
    
    // 시크릿 엔진 설정
    struct {
        bool enabled = false;
        std::string kv_path;
        int refresh_interval = 300;  // KV 갱신 간격 (초)
    } secret_kv;
    
    struct {
        bool enabled = false;
        std::string role_id;
    } secret_database_dynamic;
    
    struct {
        bool enabled = false;
        std::string role_id;
    } secret_database_static;
    
    // HTTP 설정
    int http_timeout = 30;
    int max_response_size = 4096;
};

/**
 * 설정 로더 클래스
 * INI 파일 파싱 및 설정 검증 담당
 */
class ConfigLoader {
public:
    // 기본값 정의
    static constexpr const char* DEFAULT_VAULT_URL = "http://127.0.0.1:8200";
    static constexpr const char* DEFAULT_VAULT_NAMESPACE = "";
    static constexpr const char* DEFAULT_ENTITY = "my-vault-app";
    static constexpr int DEFAULT_HTTP_TIMEOUT = 30;
    static constexpr int DEFAULT_MAX_RESPONSE_SIZE = 4096;
    static constexpr int DEFAULT_KV_REFRESH_INTERVAL = 300;  // 5분 기본값

    /**
     * 설정 파일 로드
     * @param config_file 설정 파일 경로
     * @param config 로드할 설정 객체
     * @return 성공 시 0, 실패 시 -1
     */
    static int load_config(const std::string& config_file, AppConfig& config);
    
    /**
     * 설정 정보 출력
     * @param config 출력할 설정 객체
     */
    static void print_config(const AppConfig& config);

private:
    /**
     * 문자열 트림 (앞뒤 공백 제거)
     * @param str 트림할 문자열
     * @return 트림된 문자열
     */
    static std::string trim(const std::string& str);
    
    /**
     * 문자열을 소문자로 변환
     * @param str 변환할 문자열
     * @return 소문자로 변환된 문자열
     */
    static std::string to_lower(const std::string& str);
    
    /**
     * 설정 검증
     * @param config 검증할 설정 객체
     * @return 유효한 설정이면 true, 아니면 false
     */
    static bool validate_config(const AppConfig& config);
};

#endif // CONFIG_HPP
