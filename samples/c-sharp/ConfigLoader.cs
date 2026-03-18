using System;
using System.Collections.Generic;
using System.IO;
using Microsoft.Extensions.Configuration;

namespace VaultCsharpApp
{
    /// <summary>
    /// Vault 클라이언트 설정 관리 클래스
    /// INI 파일 파싱 및 환경변수 오버라이드 지원
    /// </summary>
    public class VaultConfig
    {
        private readonly IConfiguration _config;
        private readonly string _configFile;

        /// <summary>
        /// 설정 로더 초기화
        /// </summary>
        /// <param name="configFile">설정 파일 경로 (기본값: config.ini)</param>
        public VaultConfig(string configFile = "config.ini")
        {
            _configFile = configFile;

            if (!File.Exists(configFile))
                throw new FileNotFoundException($"설정 파일을 찾을 수 없습니다: {configFile}");

            // INI 파일 파싱 (C#은 기본 INI 파서가 없어서 Microsoft.Extensions.Configuration 사용)
            _config = new ConfigurationBuilder()
                .AddIniFile(configFile, optional: false, reloadOnChange: false)
                .AddEnvironmentVariables("VAULT_")  // 환경변수로 오버라이드 가능
                .Build();
        }

        /// <summary>
        /// Vault 서버 설정 반환
        /// </summary>
        /// <returns>Vault 연결 정보 (URL, Namespace, AppRole 인증 정보 등)</returns>
        public VaultSettings GetVaultConfig() => new()
        {
            Entity   = GetWithEnvOverride("vault", "entity"),
            Url      = GetWithEnvOverride("vault", "url"),
            Namespace = GetWithEnvOverride("vault", "namespace") is { Length: > 0 } ns ? ns : null,
            RoleId   = GetWithEnvOverride("vault", "role_id"),
            SecretId = GetWithEnvOverride("vault", "secret_id")
        };

        /// <summary>
        /// KV 시크릿 설정 반환
        /// </summary>
        /// <returns>KV 시크릿 활성화 여부, 경로, 갱신 간격</returns>
        public KvSecretSettings GetKvConfig() => new()
        {
            Enabled         = GetBoolean("kv_secret", "enabled"),
            Path            = GetWithEnvOverride("kv_secret", "path"),
            RefreshInterval = GetInt("kv_secret", "refresh_interval")
        };

        /// <summary>
        /// Database Dynamic 시크릿 설정 반환
        /// </summary>
        /// <returns>Database Dynamic 시크릿 활성화 여부, 역할 ID</returns>
        public DatabaseSecretSettings GetDatabaseDynamicConfig() => new()
        {
            Enabled = GetBoolean("database_dynamic", "enabled"),
            RoleId  = GetWithEnvOverride("database_dynamic", "role_id")
        };

        /// <summary>
        /// Database Static 시크릿 설정 반환
        /// </summary>
        /// <returns>Database Static 시크릿 활성화 여부, 역할 ID</returns>
        public DatabaseSecretSettings GetDatabaseStaticConfig() => new()
        {
            Enabled = GetBoolean("database_static", "enabled"),
            RoleId  = GetWithEnvOverride("database_static", "role_id")
        };

        /// <summary>
        /// HTTP 설정 반환
        /// </summary>
        /// <returns>HTTP 타임아웃, 최대 응답 크기</returns>
        public HttpSettings GetHttpConfig() => new()
        {
            Timeout         = GetInt("http", "timeout"),
            MaxResponseSize = GetInt("http", "max_response_size")
        };

        /// <summary>
        /// 모든 설정 반환
        /// </summary>
        /// <returns>애플리케이션 전체 설정</returns>
        public AppConfig GetAllConfig() => new()
        {
            Vault           = GetVaultConfig(),
            KvSecret        = GetKvConfig(),
            DatabaseDynamic = GetDatabaseDynamicConfig(),
            DatabaseStatic  = GetDatabaseStaticConfig(),
            Http            = GetHttpConfig()
        };

        /// <summary>
        /// 현재 설정 출력
        /// </summary>
        public void PrintConfig()
        {
            var config = GetAllConfig();
            Console.WriteLine("⚙️ 현재 설정:");
            Console.WriteLine($"- Entity: {config.Vault.Entity}");
            Console.WriteLine($"- Vault URL: {config.Vault.Url}");
            Console.WriteLine($"- Namespace: {config.Vault.Namespace ?? "(없음)"}");
            Console.WriteLine($"- KV Enabled: {config.KvSecret.Enabled}");
            Console.WriteLine($"- Database Dynamic Enabled: {config.DatabaseDynamic.Enabled}");
            Console.WriteLine($"- Database Static Enabled: {config.DatabaseStatic.Enabled}");
        }

        // ── 내부 헬퍼 ────────────────────────────────────────────

        /// <summary>
        /// 환경변수 우선, 없으면 INI 값 사용
        /// </summary>
        /// <param name="section">INI 파일 섹션 이름</param>
        /// <param name="key">설정 키 이름</param>
        /// <returns>설정 값 (환경변수가 있으면 환경변수 값, 없으면 파일 값)</returns>
        private string GetWithEnvOverride(string section, string key)
        {
            // 환경변수 키 생성 (예: VAULT_URL, VAULT_ROLE_ID 등)
            var envKey = $"VAULT_{key.ToUpper()}";

            // 환경변수 값 확인
            var envValue = Environment.GetEnvironmentVariable(envKey);
            if (envValue != null) return envValue;

            // 파일에서 값 읽기
            return _config[$"{section}:{key}"] ?? string.Empty;
        }

        /// <summary>
        /// Boolean 설정 값 반환 (true/false, 1/yes 모두 지원)
        /// </summary>
        /// <param name="section">INI 파일 섹션 이름</param>
        /// <param name="key">설정 키 이름</param>
        /// <returns>Boolean 값</returns>
        private bool GetBoolean(string section, string key)
        {
            var val = _config[$"{section}:{key}"] ?? "false";
            return bool.TryParse(val, out var result) ? result
                 : val.Trim() == "1" || val.Trim().ToLower() == "yes";
        }

        /// <summary>
        /// 정수 설정 값 반환
        /// </summary>
        /// <param name="section">INI 파일 섹션 이름</param>
        /// <param name="key">설정 키 이름</param>
        /// <returns>정수 값 (파싱 실패 시 0)</returns>
        private int GetInt(string section, string key)
        {
            var val = _config[$"{section}:{key}"] ?? "0";
            return int.TryParse(val, out var result) ? result : 0;
        }
    }

    // ── 설정 모델 클래스들 ────────────────────────────────────────

    /// <summary>전체 애플리케이션 설정</summary>
    public class AppConfig
    {
        public VaultSettings           Vault           { get; set; } = new();
        public KvSecretSettings        KvSecret        { get; set; } = new();
        public DatabaseSecretSettings  DatabaseDynamic { get; set; } = new();
        public DatabaseSecretSettings  DatabaseStatic  { get; set; } = new();
        public HttpSettings            Http            { get; set; } = new();
    }

    /// <summary>Vault 서버 연결 설정</summary>
    public class VaultSettings
    {
        /// <summary>애플리케이션 엔티티 이름 (마운트 포인트 prefix로 사용)</summary>
        public string  Entity    { get; set; } = string.Empty;
        /// <summary>Vault 서버 주소</summary>
        public string  Url       { get; set; } = string.Empty;
        /// <summary>Vault 네임스페이스 (Enterprise 환경, null이면 미사용)</summary>
        public string? Namespace { get; set; }
        /// <summary>AppRole Role ID</summary>
        public string  RoleId    { get; set; } = string.Empty;
        /// <summary>AppRole Secret ID</summary>
        public string  SecretId  { get; set; } = string.Empty;
    }

    /// <summary>KV 시크릿 설정</summary>
    public class KvSecretSettings
    {
        /// <summary>KV 시크릿 사용 여부</summary>
        public bool   Enabled         { get; set; }
        /// <summary>시크릿 경로 (마운트 포인트 제외)</summary>
        public string Path            { get; set; } = string.Empty;
        /// <summary>갱신 간격 (초)</summary>
        public int    RefreshInterval { get; set; }
    }

    /// <summary>Database 시크릿 설정 (Dynamic/Static 공용)</summary>
    public class DatabaseSecretSettings
    {
        /// <summary>Database 시크릿 사용 여부</summary>
        public bool   Enabled { get; set; }
        /// <summary>Vault에 등록된 Database 역할 이름</summary>
        public string RoleId  { get; set; } = string.Empty;
    }

    /// <summary>HTTP 클라이언트 설정</summary>
    public class HttpSettings
    {
        /// <summary>요청 타임아웃 (초)</summary>
        public int Timeout         { get; set; }
        /// <summary>최대 응답 크기 (바이트)</summary>
        public int MaxResponseSize { get; set; }
    }
}
