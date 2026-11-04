from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json, time

opts = Options()
opts.add_argument('--headless=new')
driver = webdriver.Chrome(options=opts)
try:
    driver.get('http://localhost:4200/login')
    # 1. Test unauthenticated access (no user in localStorage)
    driver.execute_script("localStorage.clear();")
    driver.get('http://localhost:4200/admin')
    time.sleep(2)
    unauth_url = driver.current_url
    print(f"Unauthenticated access redirected to: {unauth_url}")
    assert '/login' in unauth_url, f"Expected redirect to /login, got {unauth_url}"

    # 2. Test Customer role access (ROLE_CUSTOMER in localStorage)
    customer_user = {
        'id': 4,
        'email': 'customer@traveleasy.com',
        'fullName': 'John Doe',
        'role': 'ROLE_CUSTOMER',
        'token': 'customer_dummy_token'
    }
    driver.execute_script(f"localStorage.setItem('traveleasy_user', '{json.dumps(customer_user)}');")
    driver.get('http://localhost:4200/admin')
    time.sleep(2)
    cust_url = driver.current_url
    print(f"Customer access redirected to: {cust_url}")
    assert '/customer' in cust_url or '/home' in cust_url, f"Expected customer redirected away from /admin, got {cust_url}"

    print("[SUCCESS] roleGuard successfully blocks unauthenticated and non-admin access!")
finally:
    driver.quit()
