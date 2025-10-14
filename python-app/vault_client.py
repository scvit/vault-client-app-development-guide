#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Vault Python Client
hvac 라이브러리를 사용한 Vault 클라이언트 구현
"""

import time
import logging
from typing import Dict, Any, Optional, Tuple
import hvac
from hvac.exceptions import VaultError


class VaultClient:
    """Vault 클라이언트 클래스"""
    
    def __init__(self, config: Dict[str, Any]):
        """
        Vault 클라이언트 초기화
        
        Args:
            config: Vault 설정 정보
        """
        self.config = config
        self.client = None
        self.token = None
        self.token_issued_time = 0
        self.token_ttl = 0
        
        # 캐시 저장소
        self.kv_cache = {}
        self.db_dynamic_cache = {}
        self.db_static_cache = {}
        
        # 로깅 설정
        self.logger = logging.getLogger(__name__)
        
        # Vault 클라이언트 초기화
        self._init_client()
    
    def _init_client(self):
        """Vault 클라이언트 초기화"""
        try:
            self.client = hvac.Client(
                url=self.config['url'],
                namespace=self.config.get('namespace')
            )
            self.logger.info(f"Vault 클라이언트 초기화 완료: {self.config['url']}")
        except Exception as e:
            self.logger.error(f"Vault 클라이언트 초기화 실패: {e}")
            raise
    
    def login(self) -> bool:
        """
        AppRole을 사용한 Vault 로그인
        
        Returns:
            로그인 성공 여부
        """
        try:
            response = self.client.auth.approle.login(
                role_id=self.config['role_id'],
                secret_id=self.config['secret_id']
            )
            
            self.token = response['auth']['client_token']
            self.token_issued_time = time.time()
            self.token_ttl = response['auth']['lease_duration']
            
            # 클라이언트에 토큰 설정
            self.client.token = self.token
            
            self.logger.info(f"Vault 로그인 성공 (TTL: {self.token_ttl}초)")
            return True
            
        except VaultError as e:
            self.logger.error(f"Vault 로그인 실패: {e}")
            return False
        except Exception as e:
            self.logger.error(f"로그인 중 오류 발생: {e}")
            return False
    
    def renew_token(self) -> bool:
        """
        토큰 갱신
        
        Returns:
            갱신 성공 여부
        """
        try:
            response = self.client.auth.token.renew_self()
            
            self.token_issued_time = time.time()
            self.token_ttl = response['auth']['lease_duration']
            
            self.logger.info(f"토큰 갱신 성공 (TTL: {self.token_ttl}초)")
            return True
            
        except VaultError as e:
            self.logger.error(f"토큰 갱신 실패: {e}")
            return False
        except Exception as e:
            self.logger.error(f"토큰 갱신 중 오류 발생: {e}")
            return False
    
    def is_token_expired(self) -> bool:
        """
        토큰 만료 여부 확인
        
        Returns:
            토큰 만료 여부
        """
        if not self.token or self.token_ttl <= 0:
            return True
        
        elapsed_time = time.time() - self.token_issued_time
        # TTL의 4/5 지점에서 갱신
        return elapsed_time >= (self.token_ttl * 0.8)
    
    def ensure_valid_token(self) -> bool:
        """
        유효한 토큰 보장
        
        Returns:
            토큰 유효성 보장 성공 여부
        """
        if not self.token or self.is_token_expired():
            return self.login()
        
        if self.is_token_expired():
            return self.renew_token()
        
        return True
    
    def get_kv_secret(self, path: str) -> Optional[Dict[str, Any]]:
        """
        KV v2 시크릿 조회
        
        Args:
            path: 시크릿 경로
            
        Returns:
            시크릿 데이터 또는 None
        """
        if not self.ensure_valid_token():
            return None
        
        try:
            # 캐시 확인
            if path in self.kv_cache:
                cached_data = self.kv_cache[path]
                current_time = time.time()
                
                # 캐시가 유효한지 확인 (5분)
                if current_time - cached_data['timestamp'] < 300:
                    self.logger.debug(f"KV 시크릿 캐시 사용: {path}")
                    return cached_data['data']
            
            # Vault에서 시크릿 조회
            response = self.client.secrets.kv.v2.read_secret_version(
                path=path,
                mount_point=f"{self.config['entity']}-kv"
            )
            
            secret_data = response['data']['data']
            metadata = response['data']['metadata']
            
            # 캐시 저장
            self.kv_cache[path] = {
                'data': secret_data,
                'metadata': metadata,
                'timestamp': time.time()
            }
            
            self.logger.info(f"KV 시크릿 조회 성공 (버전: {metadata['version']})")
            return secret_data
            
        except VaultError as e:
            self.logger.error(f"KV 시크릿 조회 실패: {e}")
            return None
        except Exception as e:
            self.logger.error(f"KV 시크릿 조회 중 오류 발생: {e}")
            return None
    
    def get_database_dynamic_secret(self, role_id: str) -> Optional[Dict[str, Any]]:
        """
        Database Dynamic 시크릿 조회
        
        Args:
            role_id: Database 역할 ID
            
        Returns:
            시크릿 데이터 또는 None
        """
        if not self.ensure_valid_token():
            return None
        
        try:
            # 캐시 확인
            cache_key = f"{role_id}"
            if cache_key in self.db_dynamic_cache:
                cached_data = self.db_dynamic_cache[cache_key]
                current_time = time.time()
                
                # TTL 기반 캐시 확인 (10초 임계값)
                remaining_ttl = cached_data['ttl'] - (current_time - cached_data['timestamp'])
                if remaining_ttl > 10:
                    self.logger.debug(f"Database Dynamic 시크릿 캐시 사용 (TTL: {int(remaining_ttl)}초)")
                    return {
                        'data': cached_data['data'],
                        'ttl': int(remaining_ttl)
                    }
            
            # Vault에서 시크릿 조회
            response = self.client.secrets.database.generate_credentials(
                name=role_id,
                mount_point=f"{self.config['entity']}-database"
            )
            
            secret_data = response['data']
            ttl = response['lease_duration']
            
            # 캐시 저장
            self.db_dynamic_cache[cache_key] = {
                'data': secret_data,
                'ttl': ttl,
                'timestamp': time.time()
            }
            
            self.logger.info(f"Database Dynamic 시크릿 조회 성공 (TTL: {ttl}초)")
            return {
                'data': secret_data,
                'ttl': ttl
            }
            
        except VaultError as e:
            self.logger.error(f"Database Dynamic 시크릿 조회 실패: {e}")
            return None
        except Exception as e:
            self.logger.error(f"Database Dynamic 시크릿 조회 중 오류 발생: {e}")
            return None
    
    def get_database_static_secret(self, role_id: str) -> Optional[Dict[str, Any]]:
        """
        Database Static 시크릿 조회
        
        Args:
            role_id: Database 역할 ID
            
        Returns:
            시크릿 데이터 또는 None
        """
        if not self.ensure_valid_token():
            return None
        
        try:
            # 캐시 확인
            cache_key = f"{role_id}"
            if cache_key in self.db_static_cache:
                cached_data = self.db_static_cache[cache_key]
                current_time = time.time()
                
                # 시간 기반 캐시 확인 (5분)
                if current_time - cached_data['timestamp'] < 300:
                    self.logger.debug(f"Database Static 시크릿 캐시 사용")
                    return cached_data['data']
            
            # Vault에서 시크릿 조회
            response = self.client.secrets.database.get_static_credentials(
                name=role_id,
                mount_point=f"{self.config['entity']}-database"
            )
            
            secret_data = response['data']
            ttl = response.get('ttl', 3600)  # 기본 TTL 1시간
            
            # 캐시 저장
            self.db_static_cache[cache_key] = {
                'data': secret_data,
                'ttl': ttl,
                'timestamp': time.time()
            }
            
            self.logger.info(f"Database Static 시크릿 조회 성공 (TTL: {ttl}초)")
            return {
                'data': secret_data,
                'ttl': ttl
            }
            
        except VaultError as e:
            self.logger.error(f"Database Static 시크릿 조회 실패: {e}")
            return None
        except Exception as e:
            self.logger.error(f"Database Static 시크릿 조회 중 오류 발생: {e}")
            return None
    
    def get_token_info(self) -> Optional[Dict[str, Any]]:
        """
        토큰 정보 조회
        
        Returns:
            토큰 정보 또는 None
        """
        if not self.ensure_valid_token():
            return None
        
        try:
            response = self.client.auth.token.lookup_self()
            return response['data']
        except VaultError as e:
            self.logger.error(f"토큰 정보 조회 실패: {e}")
            return None
        except Exception as e:
            self.logger.error(f"토큰 정보 조회 중 오류 발생: {e}")
            return None


if __name__ == "__main__":
    """Vault 클라이언트 테스트"""
    import sys
    from config_loader import VaultConfig
    
    # 로깅 설정
    logging.basicConfig(level=logging.INFO)
    
    try:
        # 설정 로드
        config_loader = VaultConfig()
        vault_config = config_loader.get_vault_config()
        
        # Vault 클라이언트 생성
        client = VaultClient(vault_config)
        
        # 로그인 테스트
        if client.login():
            print("✅ Vault 로그인 성공")
            
            # 토큰 정보 조회
            token_info = client.get_token_info()
            if token_info:
                print(f"토큰 정보: {token_info}")
        else:
            print("❌ Vault 로그인 실패")
            sys.exit(1)
            
    except Exception as e:
        print(f"테스트 실패: {e}")
        sys.exit(1)
