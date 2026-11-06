# 1. Update HTML
html_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html"
with open(html_path, "r", encoding="utf-8") as f:
    html = f.read()

target_html = '[ngModel]="step1Data.description"'
replacement_html = '[(ngModel)]="step1Data.description"'

assert target_html in html, "target_html not found"
html = html.replace(target_html, replacement_html, 1)

with open(html_path, "w", encoding="utf-8") as f:
    f.write(html)
print("Updated HTML successfully!")

# 2. Update TS
ts_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts"
with open(ts_path, "r", encoding="utf-8") as f:
    ts = f.read()

old_fn = """  onDescriptionInput(event: any): void {
    const val: string = event.target.value || '';
    if (val.length > 300) {
      this.step1Data.description = val.substring(0, 300);
    }
    this.isStep1Dirty = true;
  }"""

new_fn = """  onDescriptionInput(event: any): void {
    const val: string = event.target.value || '';
    this.step1Data.description = val.length > 300 ? val.substring(0, 300) : val;
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
  }"""

assert old_fn in ts, "old_fn not found in ts"
ts = ts.replace(old_fn, new_fn, 1)

with open(ts_path, "w", encoding="utf-8") as f:
    f.write(ts)
print("Updated TS successfully!")
