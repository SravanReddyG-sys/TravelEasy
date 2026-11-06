import mysql.connector

conn = mysql.connector.connect(host='localhost', user='root', password='Gsravanreddy@7989', database='traveleasy_hotel_db')
cursor = conn.cursor()

cursor.execute("UPDATE hotel_properties SET property_segment = 'STANDARD' WHERE property_segment IN ('BUDGET', 'Budget')")
u1 = cursor.rowcount
cursor.execute("UPDATE hotel_properties SET property_segment = 'COMFORT' WHERE property_segment IN ('MIDSCALE', 'Midscale')")
u2 = cursor.rowcount
cursor.execute("UPDATE hotel_properties SET property_segment = 'PREMIUM' WHERE property_segment IN ('UPSCALE', 'Upscale')")
u3 = cursor.rowcount

conn.commit()
print(f"Migrated legacy segments: {u1} Standard, {u2} Comfort, {u3} Premium")

cursor.close()
conn.close()
