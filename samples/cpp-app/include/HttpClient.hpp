#ifndef HTTP_CLIENT_HPP
#define HTTP_CLIENT_HPP

#include <string>
#include <vector>
#include <map>
#include <memory>
#include <curl/curl.h>

/**
 * HTTP 응답 구조체
 */
struct HttpResponse {
    std::string data;
    long status_code = 0;
    std::map<std::string, std::string> headers;
    
    // 응답이 성공적인지 확인
    bool is_success() const {
        return status_code >= 200 && status_code < 300;
    }
    
    // 응답 데이터가 비어있는지 확인
    bool is_empty() const {
        return data.empty();
    }
};

/**
 * HTTP 클라이언트 클래스
 * libcurl의 C++ 래퍼로 RAII 패턴 적용
 */
class HttpClient {
public:
    /**
     * 생성자
     * @param timeout HTTP 요청 타임아웃 (초)
     */
    explicit HttpClient(int timeout = 30);
    
    /**
     * 소멸자
     * libcurl 리소스 자동 정리
     */
    ~HttpClient();
    
    // 복사 생성자 및 대입 연산자 비활성화
    HttpClient(const HttpClient&) = delete;
    HttpClient& operator=(const HttpClient&) = delete;
    
    // 이동 생성자 및 대입 연산자
    HttpClient(HttpClient&& other) noexcept;
    HttpClient& operator=(HttpClient&& other) noexcept;
    
    /**
     * HTTP GET 요청
     * @param url 요청할 URL
     * @param headers 추가 헤더 (선택사항)
     * @return HTTP 응답
     */
    HttpResponse get(const std::string& url, 
                    const std::map<std::string, std::string>& headers = {}) const;
    
    /**
     * HTTP POST 요청
     * @param url 요청할 URL
     * @param data POST 데이터
     * @param headers 추가 헤더 (선택사항)
     * @return HTTP 응답
     */
    HttpResponse post(const std::string& url, 
                     const std::string& data,
                     const std::map<std::string, std::string>& headers = {}) const;
    
    /**
     * HTTP POST 요청 (JSON 데이터)
     * @param url 요청할 URL
     * @param json_data JSON 문자열
     * @param headers 추가 헤더 (선택사항)
     * @return HTTP 응답
     */
    HttpResponse post_json(const std::string& url, 
                          const std::string& json_data,
                          const std::map<std::string, std::string>& headers = {}) const;
    
    /**
     * 타임아웃 설정
     * @param timeout 새로운 타임아웃 값 (초)
     */
    void set_timeout(int timeout);
    
    /**
     * SSL 검증 설정
     * @param verify true면 SSL 검증, false면 검증 안함
     */
    void set_ssl_verify(bool verify);

private:
    CURL* curl_;
    int timeout_;
    bool ssl_verify_;
    
    /**
     * libcurl 초기화
     * @return 성공 시 true, 실패 시 false
     */
    bool initialize_curl();
    
    /**
     * HTTP 헤더 설정
     * @param headers 설정할 헤더 맵
     * @return 설정된 curl_slist 포인터 (메모리 해제 필요)
     */
    struct curl_slist* setup_headers(const std::map<std::string, std::string>& headers) const;
    
    /**
     * libcurl 콜백 함수 (정적)
     * @param contents 수신된 데이터
     * @param size 데이터 크기
     * @param nmemb 데이터 개수
     * @param userp 사용자 데이터 (HttpResponse 포인터)
     * @return 처리된 바이트 수
     */
    static size_t write_callback(void* contents, size_t size, size_t nmemb, void* userp);
    
    /**
     * 헤더 콜백 함수 (정적)
     * @param contents 수신된 헤더
     * @param size 헤더 크기
     * @param nmemb 헤더 개수
     * @param userp 사용자 데이터 (HttpResponse 포인터)
     * @return 처리된 바이트 수
     */
    static size_t header_callback(void* contents, size_t size, size_t nmemb, void* userp);
};

#endif // HTTP_CLIENT_HPP
