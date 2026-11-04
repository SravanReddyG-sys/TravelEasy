import urllib.request, json

url = 'http://localhost:8080/api/auth/login'
test_emails = ['admin@traveleasy.com', 'customer@traveleasy.com', 'shavali@gmail.com']

for email in test_emails:
    data = json.dumps({'email': email, 'password': 'Password123!'}).encode()
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode())
            role = body.get('role')
            token = body.get('token', '')
            print(f"SUCCESS: {email} -> role={role}, token_len={len(token)}")
            if role == 'ROLE_ADMIN':
                with open('scratch/admin_user.json', 'w') as out:
                    json.dump(body, out)
    except urllib.error.HTTPError as e:
        print(f"FAILED: {email} -> {e.code}: {e.read().decode()}")
