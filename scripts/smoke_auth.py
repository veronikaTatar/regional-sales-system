#!/usr/bin/env python3
"""End-to-end auth checks against a running server. Creates one uniquely named manager.
Run: python scripts/smoke_auth.py [http://localhost:8080]
No external Python packages or printed credentials. Never run against production.
"""
import http.cookiejar
import json
import secrets
import sys
import urllib.error
import urllib.request

base = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

def request(method, path, payload=None, csrf=None):
    headers = {'Accept': 'application/json'}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    if csrf:
        headers[csrf['headerName']] = csrf['token']
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        response = client.open(req)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read()
    return response.status, json.loads(body) if body else None

def expect(status, result, label):
    actual, body = result
    assert actual == status, f'{label}: expected {status}, received {actual}: {body}'
    print(f'PASS {label}')
    return body

expect(401, request('GET', '/api/auth/me'), 'anonymous access denied')
expect(403, request('POST', '/api/auth/register', {}), 'registration requires CSRF')
csrf = expect(200, request('GET', '/api/auth/csrf'), 'CSRF token')
login = 'smoke_' + secrets.token_hex(6)
password = 'Smoke!A9_' + secrets.token_urlsafe(16)
payload = {'login': login, 'email': login + '@example.com', 'fullName': 'Проверка приложения', 'password': password}
status, user = request('POST', '/api/auth/register', payload, csrf)
assert status == 201, f'registration expected 201, got {status}: {user}'
assert user['role'] == 'MANAGER' and user['login'] == login
print('PASS registration persists a manager')
expect(200, request('GET', '/api/auth/me'), 'authenticated profile')
regions = expect(200, request('GET', '/api/regions'), 'regional access request')
assert regions == [], f'unassigned manager must not see regions: {regions}'
expect(403, request('GET', '/api/users'), 'manager cannot list users')
csrf = expect(200, request('GET', '/api/auth/csrf'), 'CSRF after registration')
expect(204, request('POST', '/api/auth/logout', csrf=csrf), 'logout')
expect(401, request('GET', '/api/auth/me'), 'logged out access denied')
csrf = expect(200, request('GET', '/api/auth/csrf'), 'CSRF after logout')
expect(401, request('POST', '/api/auth/login', {'login': login, 'password': 'WrongPassword123!'}, csrf), 'wrong password rejected')
expect(200, request('POST', '/api/auth/login', {'login': login, 'password': password}, csrf), 'login uses saved credentials')
csrf = expect(200, request('GET', '/api/auth/csrf'), 'CSRF after login')
expect(204, request('POST', '/api/auth/logout', csrf=csrf), 'final logout')
csrf = expect(200, request('GET', '/api/auth/csrf'), 'CSRF for duplicate check')
expect(409, request('POST', '/api/auth/register', payload, csrf), 'duplicate registration rejected')
print('Auth smoke test passed. One smoke_* account remains for persistence inspection.')
