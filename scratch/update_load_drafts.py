ts_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts"
with open(ts_path, "r", encoding="utf-8") as f:
    code = f.read()

target = """    this.hotelService.getHotelsByManager(this.currentUser.id).subscribe({"""
replacement = """    this.loadManagerDrafts();
    this.hotelService.getHotelsByManager(this.currentUser.id).subscribe({"""

if target in code and "this.loadManagerDrafts();" not in code[:15000]:
    code = code.replace(target, replacement, 1)
    with open(ts_path, "w", encoding="utf-8") as f:
        f.write(code)
    print("Added this.loadManagerDrafts() to loadInitialData() successfully!")
else:
    print("Target not found or already added")
