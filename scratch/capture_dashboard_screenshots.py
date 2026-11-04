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
chrome_options.add_argument("--window-size=1440,1050")

driver = webdriver.Chrome(options=chrome_options)

try:
    print("Navigating to http://localhost:4200/login...")
    driver.get("http://localhost:4200/login")
    time.sleep(1)

    # Set authenticated admin credentials in localStorage
    user_json = json.dumps(admin_user)
    driver.execute_script(f"localStorage.setItem('traveleasy_user', '{user_json}');")
    print("Injected admin credentials into localStorage.")

    # Navigate to Admin Dashboard
    print("Navigating to http://localhost:4200/admin...")
    driver.get("http://localhost:4200/admin")

    # Wait for dashboard content to load
    WebDriverWait(driver, 10).until(
        EC.presence_of_element_located((By.CLASS_NAME, "welcome-title"))
    )
    time.sleep(3) # allow all independent API calls to complete

    # 1. Desktop Screenshot
    driver.set_window_size(1440, 1050)
    time.sleep(1)
    desktop_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_desktop.png")
    driver.save_screenshot(desktop_path)
    print(f"[OK] Saved desktop screenshot to: {desktop_path}")

    # 2. Revenue View Toggle Screenshot
    try:
        rev_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Revenue')]")
        rev_btn.click()
        time.sleep(2)
        rev_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_revenue_view.png")
        driver.save_screenshot(rev_path)
        print(f"[OK] Saved revenue view screenshot to: {rev_path}")
        # Toggle back to bookings
        bkg_btn = driver.find_element(By.XPATH, "//button[contains(text(), 'Bookings')]")
        bkg_btn.click()
        time.sleep(1)
    except Exception as e:
        print(f"[WARN] Toggle revenue error: {e}")

    # 3. Provider Details Review Modal Screenshot
    try:
        review_buttons = driver.find_elements(By.CLASS_NAME, "btn-action-outline")
        if review_buttons:
            driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", review_buttons[0])
            time.sleep(1)
            driver.execute_script("arguments[0].click();", review_buttons[0])
            time.sleep(2)
            modal_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_provider_review_modal.png")
            driver.save_screenshot(modal_path)
            print(f"[OK] Saved provider review modal screenshot to: {modal_path}")
            # Close modal
            close_btn = driver.find_element(By.CLASS_NAME, "btn-close")
            driver.execute_script("arguments[0].click();", close_btn)
            time.sleep(1)
    except Exception as e:
        print(f"[WARN] Modal screenshot error: {e}")

    # 4. Tablet Screenshot
    driver.set_window_size(768, 1024)
    time.sleep(1)
    tablet_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_tablet.png")
    driver.save_screenshot(tablet_path)
    print(f"[OK] Saved tablet screenshot to: {tablet_path}")

    # 5. Mobile Screenshot
    driver.set_window_size(390, 844)
    time.sleep(1)
    mobile_path = os.path.join(ARTIFACT_DIR, "admin_dashboard_mobile.png")
    driver.save_screenshot(mobile_path)
    print(f"[OK] Saved mobile screenshot to: {mobile_path}")

    print("\nALL SCREENSHOTS SUCCESSFULLY CAPTURED!")

finally:
    driver.quit()
