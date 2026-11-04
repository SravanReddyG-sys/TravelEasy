from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json, time

opts = Options()
opts.add_argument('--headless=new')
opts.set_capability('goog:loggingPrefs', {'browser': 'ALL'})
driver = webdriver.Chrome(options=opts)
try:
    driver.get('http://localhost:4200/login')
    with open('scratch/admin_user.json') as f:
        u = json.load(f)
    user_str = json.dumps(u)
    driver.execute_script(f"localStorage.setItem('traveleasy_user', '{user_str}');")
    driver.get('http://localhost:4200/admin')
    time.sleep(3)
    logs = driver.get_log('browser')
    print(f'Total browser logs: {len(logs)}')
    severe = [l for l in logs if l['level'] == 'SEVERE']
    print(f'Severe errors count: {len(severe)}')
    for s in severe:
        print("SEVERE:", s['message'])
finally:
    driver.quit()
