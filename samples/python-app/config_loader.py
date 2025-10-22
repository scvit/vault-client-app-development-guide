#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Vault Python Client Application Configuration Loader
설정 파일 로드 및 환경변수 오버라이드 지원
"""

import os
import configparser
from typing import Dict, Any, Optional


class VaultConfig:
    """Vault 클라이언트 설정 관리 클래스"""
    
    def __init__(self, config_file: str = "config.ini"):
        """
        설정 로더 초기화
        
        Args:
            config_file: 설정 파일 경로
        """
        self.config_file = config_file
        self.config = configparser.ConfigParser()
        self._load_config()
    
    def _load_config(self):
        """설정 파일 로드"""
        if not os.path.exists(self.config_file):
            raise FileNotFoundError(f"설정 파일을 찾을 수 없습니다: {self.config_file}")
        
        self.config.read(self.config_file, encoding='utf-8')
    
    def get_vault_config(self) -> Dict[str, Any]:
        """Vault 서버 설정 반환"""
        return {
            'entity': self._get_with_env_override('vault', 'entity'),
            'url': self._get_with_env_override('vault', 'url'),
            'namespace': self._get_with_env_override('vault', 'namespace') or None,
            'role_id': self._get_with_env_override('vault', 'role_id'),
            'secret_id': self._get_with_env_override('vault', 'secret_id')
        }
    
    def get_kv_config(self) -> Dict[str, Any]:
        """KV 시크릿 설정 반환"""
        return {
            'enabled': self._get_boolean('kv_secret', 'enabled'),
            'path': self._get_with_env_override('kv_secret', 'path'),
            'refresh_interval': self._get_int('kv_secret', 'refresh_interval')
        }
    
    def get_database_dynamic_config(self) -> Dict[str, Any]:
        """Database Dynamic 시크릿 설정 반환"""
        return {
            'enabled': self._get_boolean('database_dynamic', 'enabled'),
            'role_id': self._get_with_env_override('database_dynamic', 'role_id')
        }
    
    def get_database_static_config(self) -> Dict[str, Any]:
        """Database Static 시크릿 설정 반환"""
        return {
            'enabled': self._get_boolean('database_static', 'enabled'),
            'role_id': self._get_with_env_override('database_static', 'role_id')
        }
    
    def get_http_config(self) -> Dict[str, Any]:
        """HTTP 설정 반환"""
        return {
            'timeout': self._get_int('http', 'timeout'),
            'max_response_size': self._get_int('http', 'max_response_size')
        }
    
    def _get_with_env_override(self, section: str, key: str) -> str:
        """
        환경변수 오버라이드 지원 설정 값 반환
        
        Args:
            section: 설정 섹션
            key: 설정 키
            
        Returns:
            설정 값 (환경변수가 있으면 환경변수 값, 없으면 파일 값)
        """
        # 환경변수 키 생성 (예: VAULT_URL, VAULT_ROLE_ID 등)
        env_key = f"VAULT_{key.upper()}"
        
        # 환경변수 값 확인
        env_value = os.getenv(env_key)
        if env_value is not None:
            return env_value
        
        # 파일에서 값 읽기
        return self.config.get(section, key)
    
    def _get_boolean(self, section: str, key: str) -> bool:
        """Boolean 값 반환"""
        return self.config.getboolean(section, key)
    
    def _get_int(self, section: str, key: str) -> int:
        """정수 값 반환"""
        return self.config.getint(section, key)
    
    def get_all_config(self) -> Dict[str, Any]:
        """모든 설정 반환"""
        return {
            'vault': self.get_vault_config(),
            'kv_secret': self.get_kv_config(),
            'database_dynamic': self.get_database_dynamic_config(),
            'database_static': self.get_database_static_config(),
            'http': self.get_http_config()
        }
    
    def print_config(self):
        """현재 설정 출력"""
        print("⚙️ 현재 설정:")
        config = self.get_all_config()
        
        vault_config = config['vault']
        print(f"- Entity: {vault_config['entity']}")
        print(f"- Vault URL: {vault_config['url']}")
        print(f"- KV Enabled: {config['kv_secret']['enabled']}")
        print(f"- Database Dynamic Enabled: {config['database_dynamic']['enabled']}")
        print(f"- Database Static Enabled: {config['database_static']['enabled']}")


if __name__ == "__main__":
    """설정 로더 테스트"""
    try:
        config = VaultConfig()
        config.print_config()
    except Exception as e:
        print(f"설정 로드 실패: {e}")
