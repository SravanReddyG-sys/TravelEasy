import os
import mysql.connector

def check(db_name, sql):
    password = os.getenv('MYSQL_ROOT_PASSWORD', '')
    user = os.getenv('MYSQL_USER', 'root')
    host = os.getenv('MYSQL_HOST', 'localhost')
    conn = mysql.connector.connect(host=host, user=user, password=password, database=db_name)
    cur = conn.cursor()
    cur.execute(sql)
    res = cur.fetchall()
    conn.close()
    return res

print("Bookings by Status:", check('traveleasy_booking_db', "SELECT status, COUNT(*) FROM bookings GROUP BY status"))
print("Bookings by Service (Confirmed):", check('traveleasy_booking_db', "SELECT service_type, COUNT(*) FROM bookings WHERE status = 'CONFIRMED' GROUP BY service_type"))
print("Reviews Count:", check('traveleasy_review_db', "SELECT COUNT(*) FROM reviews"))
print("Users Count:", check('traveleasy_auth_db', "SELECT COUNT(*), role FROM users GROUP BY role"))
print("Pending Providers:", check('traveleasy_auth_db', "SELECT COUNT(*) FROM users WHERE role IN ('ROLE_BUS_OPERATOR', 'ROLE_HOTEL_MANAGER') AND verification_status = 'UNDER_REVIEW'"))
