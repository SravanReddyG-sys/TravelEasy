import urllib.request, json

with open('scratch/admin_user.json', 'r') as f:
    admin_data = json.load(f)

token = admin_data['token']
headers = {
    'Authorization': f'Bearer {token}',
    'Accept': 'application/json'
}

endpoints = [
    ('User Stats', 'http://localhost:8080/api/users/admin/stats'),
    ('Pending Providers', 'http://localhost:8080/api/users/admin/pending-providers?page=0&size=5'),
    ('Booking Stats', 'http://localhost:8080/api/bookings/admin/stats'),
    ('Booking Trends (7d)', 'http://localhost:8080/api/bookings/admin/trends?days=7'),
    ('Recent Bookings (5)', 'http://localhost:8080/api/bookings/admin/recent?limit=5'),
    ('Payment Stats', 'http://localhost:8080/api/payments/admin/stats'),
    ('Revenue Trends (7d)', 'http://localhost:8080/api/payments/admin/revenue-trends?days=7'),
    ('Review Stats', 'http://localhost:8080/api/reviews/admin/stats'),
    ('Pending Reviews', 'http://localhost:8080/api/reviews/admin/pending?page=0&size=10'),
]

print("=== TESTING ALL ADMIN APIS THROUGH API GATEWAY ===")
all_ok = True
for name, url in endpoints:
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            print(f"[OK 200] {name}: {data}")
    except urllib.error.HTTPError as e:
        print(f"[ERR {e.code}] {name}: {e.read().decode()}")
        all_ok = False

if all_ok:
    print("\nALL 9 ADMIN DASHBOARD APIS RESPONDED WITH 200 OK THROUGH API GATEWAY!")
else:
    print("\nSome endpoints encountered errors.")
