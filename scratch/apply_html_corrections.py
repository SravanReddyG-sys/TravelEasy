# Update HTML
html_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html"
with open(html_path, "r", encoding="utf-8") as f:
    html = f.read()

old_class_block = """                    <select class="custom-form-select"
                            [(ngModel)]="step1Data.propertyClassification"
                            name="propertyClassification"
                            (change)="onClassificationChange()">
                      <option value="" selected>Select classification</option>
                      <option value="5_STAR_DELUXE">5-Star Deluxe</option>
                      <option value="5_STAR">5-Star</option>
                      <option value="4_STAR">4-Star</option>
                      <option value="3_STAR">3-Star</option>
                      <option value="2_STAR">2-Star</option>
                      <option value="1_STAR">1-Star</option>
                      <option value="HERITAGE">Heritage</option>
                      <option value="NOT_CLASSIFIED">Not Classified</option>
                      <option value="OTHER">Other</option>
                    </select>"""

new_class_block = """                    <select class="custom-form-select"
                            [(ngModel)]="step1Data.propertyClassification"
                            name="propertyClassification"
                            (change)="onClassificationChange()">
                      <option value="" selected>Select classification</option>
                      <option value="NOT_CLASSIFIED">Not officially classified</option>
                      <option value="1_STAR">1 Star</option>
                      <option value="2_STAR">2 Star</option>
                      <option value="3_STAR">3 Star</option>
                      <option value="4_STAR">4 Star</option>
                      <option value="5_STAR">5 Star</option>
                      <option value="5_STAR_DELUXE">5 Star Deluxe</option>
                      <option value="OTHER">Other recognized classification</option>
                    </select>"""

assert old_class_block in html, "old_class_block not found in html"
html = html.replace(old_class_block, new_class_block, 1)

# Custom classification placeholder update
old_custom_ph = 'placeholder="Specify custom classification (e.g. Eco Resort)"'
new_custom_ph = 'placeholder="Specify other recognized classification (e.g. Heritage Hotel, Eco Resort)"'
if old_custom_ph in html:
    html = html.replace(old_custom_ph, new_custom_ph, 1)

# Segment block update
old_seg_block = """                    <select class="custom-form-select"
                            [(ngModel)]="step1Data.propertySegment"
                            name="propertySegment"
                            (change)="onStep1FieldChange()"
                            required>
                      <option value="" disabled selected>Select property segment</option>
                      <option value="Budget">Budget</option>
                      <option value="Midscale">Midscale</option>
                      <option value="Upscale">Upscale</option>
                      <option value="Luxury">Luxury</option>
                      <option value="Ultra Luxury">Ultra Luxury</option>
                    </select>"""

new_seg_block = """                    <select class="custom-form-select"
                            [(ngModel)]="step1Data.propertySegment"
                            name="propertySegment"
                            (change)="onStep1FieldChange()"
                            required>
                      <option value="" disabled selected>Select property segment</option>
                      <option value="Standard">Standard</option>
                      <option value="Comfort">Comfort</option>
                      <option value="Premium">Premium</option>
                      <option value="Luxury">Luxury</option>
                      <option value="Ultra Luxury">Ultra Luxury</option>
                    </select>"""

assert old_seg_block in html, "old_seg_block not found in html"
html = html.replace(old_seg_block, new_seg_block, 1)

with open(html_path, "w", encoding="utf-8") as f:
    f.write(html)

print("Updated hotel-manager-dashboard.component.html successfully!")
