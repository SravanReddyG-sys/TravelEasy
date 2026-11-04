import json
import time
import os
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

ARTIFACT_DIR = r"C:\Users\reddy\.gemini\antigravity\brain\eea23d8b-ad0e-4f6f-93fd-d2d485cfbd8d"

with open("scratch/admin_user.json", "r") as f:
    admin_user = json.load(f)

chrome_options = Options()
chrome_options.add_argument("--headless=new")
chrome_options.add_argument("--disable-gpu")
chrome_options.add_argument("--no-sandbox")
chrome_options.add_argument("--disable-dev-shm-usage")
chrome_options.add_argument("--window-size=1440,900")

driver = webdriver.Chrome(options=chrome_options)

try:
    print("Navigating to http://localhost:4200/login...")
    driver.get("http://localhost:4200/login")
    time.sleep(1)

    user_json = json.dumps(admin_user)
    driver.execute_script(f"localStorage.setItem('traveleasy_user', '{user_json}');")

    print("Navigating to http://localhost:4200/admin...")
    driver.get("http://localhost:4200/admin")

    WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CLASS_NAME, "welcome-title"))
    )
    time.sleep(3)

    # Capture desktop screenshot
    desktop_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_desktop.png")
    driver.save_screenshot(desktop_path)
    print(f"[OK] Saved desktop screenshot to: {desktop_path}")

    # Capture header element screenshot specifically
    header_el = driver.find_element(By.CLASS_NAME, "shell-header")
    header_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_header.png")
    header_el.screenshot(header_path)
    print(f"[OK] Saved header screenshot to: {header_path}")

finally:
    driver.quit()
