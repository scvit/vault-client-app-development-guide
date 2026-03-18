using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Microsoft.Extensions.Logging;
using VaultSharp;
using VaultSharp.V1.AuthMethods.AppRole;
using VaultSharp.V1.Commons;

namespace VaultCsharpApp
{
    /// <summary>
    /// Vault 클라이언트 클래스
    /// VaultSharp 라이브러리 사용
    /// </summary>
    public class VaultClient
    {
        private readonly VaultSettings _config;
        private readonly ILogger<VaultClient> _logger;

        private IVaultClient? _client;
        private string? _token;
        private long   _tokenIssuedTime;  // Unix timestamp (초)
        private int    _tokenTtl;

        // ── 캐시 ────────────────────────────────────────────────
        private readonly Dictionary<string, KvCacheEntry>        _kvCache        = new();
        private readonly Dictionary<string, DbDynamicCacheEntry> _dbDynamicCache = new();
        private readonly Dictionary<string, DbStaticCacheEntry>  _dbStaticCache  = new();

        /// <summary>
        /// Vault 클라이언트 초기화
        /// </summary>
        /// <param name="config">Vault 서버 연결 설정</param>
        /// <param name="logger">로거 인스턴스</param>
        public VaultClient(VaultSettings config, ILogger<VaultClient> logger)
        {
            _config = config;
            _logger = logger;
            InitClient();
        }

        // ── 초기화 ───────────────────────────────────────────────

        private void InitClient()
        {
            _logger.LogInformation("Vault 클라이언트 초기화 완료: {Url} (Namespace: {Namespace})",
                _config.Url, _config.Namespace ?? "(없음)");
        }

        // ── 인증 ────────────────────────────────────────────────

        /// <summary>
        /// AppRole을 사용한 Vault 로그인
        /// </summary>
        /// <returns>로그인 성공 여부</returns>
        public async Task<bool> LoginAsync()
        {
            try
            {
                var authMethod = new AppRoleAuthMethodInfo(_config.RoleId, _config.SecretId);
                var vaultClientSettings = new VaultClientSettings(_config.Url, authMethod)
                {
                    MyHttpClientProviderFunc = null,
                    // Namespace가 null이면 네임스페이스 미사용 (Community Edition 호환)
                    Namespace = _config.Namespace
                };

                _client = new VaultSharp.VaultClient(vaultClientSettings);

                // 실제 로그인 확인을 위해 토큰 조회
                var tokenInfo = await _client.V1.Auth.Token.LookupSelfAsync();
                _token = tokenInfo.Data.Id;
                _tokenIssuedTime = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                _tokenTtl = 3600; // 기본 TTL 1시간

                _logger.LogInformation("Vault 로그인 성공 (TTL: {Ttl}초)", _tokenTtl);
                return true;
            }
            catch (Exception e)
            {
                _logger.LogError("Vault 로그인 실패: {Error}", e.Message);
                return false;
            }
        }

        /// <summary>
        /// 토큰 갱신
        /// </summary>
        /// <returns>갱신 성공 여부</returns>
        public async Task<bool> RenewTokenAsync()
        {
            if (_client == null) return false;
            try
            {
                await _client.V1.Auth.Token.RenewSelfAsync();
                _tokenIssuedTime = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                _logger.LogInformation("토큰 갱신 성공");
                return true;
            }
            catch (Exception e)
            {
                _logger.LogError("토큰 갱신 실패: {Error}", e.Message);
                return false;
            }
        }

        /// <summary>
        /// 토큰 만료 여부 확인 (TTL의 80% 지점에서 갱신)
        /// </summary>
        /// <returns>토큰 만료 여부</returns>
        public bool IsTokenExpired()
        {
            if (_token == null || _tokenTtl <= 0) return true;
            var elapsed = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - _tokenIssuedTime;
            // TTL의 80% 지점에서 갱신
            return elapsed >= _tokenTtl * 0.8;
        }

        /// <summary>
        /// 유효한 토큰 보장
        /// </summary>
        /// <returns>토큰 유효성 보장 성공 여부</returns>
        public async Task<bool> EnsureValidTokenAsync()
        {
            if (_token == null || IsTokenExpired())
                return await LoginAsync();
            return true;
        }

        // ── 시크릿 조회 ─────────────────────────────────────────

        /// <summary>
        /// KV v2 시크릿 조회 (5분 캐시)
        /// </summary>
        /// <param name="path">시크릿 경로 (마운트 포인트 제외)</param>
        /// <returns>시크릿 데이터 또는 null</returns>
        public async Task<Dictionary<string, object>?> GetKvSecretAsync(string path)
        {
            if (!await EnsureValidTokenAsync()) return null;

            // 캐시 확인 (5분 유효)
            if (_kvCache.TryGetValue(path, out var cached))
            {
                var age = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - cached.Timestamp;
                if (age < 300)
                {
                    _logger.LogDebug("KV 시크릿 캐시 사용: {Path}", path);
                    return cached.Data;
                }
            }

            try
            {
                // 마운트 포인트: {entity}-kv (예: my-vault-app-kv)
                var mountPoint = $"{_config.Entity}-kv";
                var secret = await _client!.V1.Secrets.KeyValue.V2
                    .ReadSecretAsync(path: path, mountPoint: mountPoint);

                var data = secret.Data.Data?.ToDictionary(k => k.Key, v => v.Value)
                           ?? new Dictionary<string, object>();

                // 캐시 저장
                _kvCache[path] = new KvCacheEntry
                {
                    Data      = data,
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                };

                _logger.LogInformation("KV 시크릿 조회 성공 (버전: {Ver})", secret.Data.Metadata.Version);
                return data;
            }
            catch (Exception e)
            {
                _logger.LogError("KV 시크릿 조회 실패: {Error}", e.Message);
                return null;
            }
        }

        /// <summary>
        /// Database Dynamic 시크릿 조회 (TTL 기반 캐시, 10초 임계값)
        /// </summary>
        /// <param name="roleId">Vault에 등록된 Database 역할 이름</param>
        /// <returns>시크릿 데이터(username/password)와 TTL, 또는 null</returns>
        public async Task<SecretResult?> GetDatabaseDynamicSecretAsync(string roleId)
        {
            if (!await EnsureValidTokenAsync()) return null;

            // 캐시 확인 (TTL 기반, 10초 임계값)
            if (_dbDynamicCache.TryGetValue(roleId, out var cached))
            {
                var remainingTtl = cached.Ttl - (DateTimeOffset.UtcNow.ToUnixTimeSeconds() - cached.Timestamp);
                if (remainingTtl > 10)
                {
                    _logger.LogDebug("Database Dynamic 시크릿 캐시 사용 (TTL: {Ttl}초)", remainingTtl);
                    return new SecretResult { Data = cached.Data, Ttl = (int)remainingTtl };
                }
            }

            try
            {
                // 마운트 포인트: {entity}-database (예: my-vault-app-database)
                var mountPoint = $"{_config.Entity}-database";
                var secret = await _client!.V1.Secrets.Database
                    .GetCredentialsAsync(roleId, mountPoint: mountPoint);

                var data = new Dictionary<string, string>
                {
                    ["username"] = secret.Data.Username,
                    ["password"] = secret.Data.Password
                };

                var ttl = (int)secret.LeaseDurationSeconds;

                // 캐시 저장
                _dbDynamicCache[roleId] = new DbDynamicCacheEntry
                {
                    Data      = data,
                    Ttl       = ttl,
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                };

                _logger.LogInformation("Database Dynamic 시크릿 조회 성공 (TTL: {Ttl}초)", ttl);
                return new SecretResult { Data = data, Ttl = ttl };
            }
            catch (Exception e)
            {
                _logger.LogError("Database Dynamic 시크릿 조회 실패: {Error}", e.Message);
                return null;
            }
        }

        /// <summary>
        /// Database Static 시크릿 조회 (5분 캐시)
        /// </summary>
        /// <param name="roleId">Vault에 등록된 Database 역할 이름</param>
        /// <returns>시크릿 데이터(username/password)와 TTL, 또는 null</returns>
        public async Task<SecretResult?> GetDatabaseStaticSecretAsync(string roleId)
        {
            if (!await EnsureValidTokenAsync()) return null;

            // 캐시 확인 (5분 유효)
            if (_dbStaticCache.TryGetValue(roleId, out var cached))
            {
                var age = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - cached.Timestamp;
                if (age < 300)
                {
                    _logger.LogDebug("Database Static 시크릿 캐시 사용");
                    return cached.Result;
                }
            }

            try
            {
                // 마운트 포인트: {entity}-database (예: my-vault-app-database)
                var mountPoint = $"{_config.Entity}-database";
                var secret = await _client!.V1.Secrets.Database
                    .GetStaticCredentialsAsync(roleId, mountPoint: mountPoint);

                var data = new Dictionary<string, string>
                {
                    ["username"] = secret.Data.Username,
                    ["password"] = secret.Data.Password
                };

                var ttl = (int)(secret.LeaseDurationSeconds > 0 ? secret.LeaseDurationSeconds : 3600);
                var result = new SecretResult { Data = data, Ttl = ttl };

                // 캐시 저장
                _dbStaticCache[roleId] = new DbStaticCacheEntry
                {
                    Result    = result,
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
                };

                _logger.LogInformation("Database Static 시크릿 조회 성공 (TTL: {Ttl}초)", ttl);
                return result;
            }
            catch (Exception e)
            {
                _logger.LogError("Database Static 시크릿 조회 실패: {Error}", e.Message);
                return null;
            }
        }
    }

    // ── 결과/캐시 모델 ──────────────────────────────────────────

    /// <summary>시크릿 조회 결과</summary>
    public class SecretResult
    {
        /// <summary>시크릿 데이터 (username, password 등)</summary>
        public Dictionary<string, string> Data { get; set; } = new();
        /// <summary>남은 TTL (초)</summary>
        public int Ttl { get; set; }
    }

    /// <summary>KV 시크릿 캐시 항목</summary>
    internal class KvCacheEntry
    {
        public Dictionary<string, object> Data      { get; set; } = new();
        public long                        Timestamp { get; set; }
    }

    /// <summary>Database Dynamic 시크릿 캐시 항목</summary>
    internal class DbDynamicCacheEntry
    {
        public Dictionary<string, string> Data      { get; set; } = new();
        public int                         Ttl       { get; set; }
        public long                        Timestamp { get; set; }
    }

    /// <summary>Database Static 시크릿 캐시 항목</summary>
    internal class DbStaticCacheEntry
    {
        public SecretResult Result    { get; set; } = new();
        public long         Timestamp { get; set; }
    }
}
