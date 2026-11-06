ts_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts"
with open(ts_path, "r", encoding="utf-8") as f:
    code = f.read()

old_draft_init = """    if (draft) {
      this.step1Data = {
        id: draft.id || null,
        name: draft.name || '',
        propertyType: draft.propertyType || '',
        propertyClassification: draft.propertyClassification || '',
        customClassification: draft.customClassification || '',
        propertySegment: draft.propertySegment || '',
        brandName: draft.brandName || '',
        description: draft.description || ''
      };"""

new_draft_init = """    if (draft) {
      let segment = draft.propertySegment || '';
      const upperSeg = segment.trim().toUpperCase().replace(/_/g, ' ');
      if (upperSeg === 'BUDGET' || upperSeg === 'STANDARD') segment = 'Standard';
      else if (upperSeg === 'MIDSCALE' || upperSeg === 'COMFORT') segment = 'Comfort';
      else if (upperSeg === 'UPSCALE' || upperSeg === 'PREMIUM') segment = 'Premium';
      else if (upperSeg === 'LUXURY') segment = 'Luxury';
      else if (upperSeg === 'ULTRA LUXURY') segment = 'Ultra Luxury';

      let classification = draft.propertyClassification || '';
      const upperClass = classification.trim().toUpperCase().replace(/ /g, '_');
      let customClass = draft.customClassification || '';
      if (upperClass === 'NOT_OFFICIALLY_CLASSIFIED' || upperClass === 'NOT_CLASSIFIED') {
        classification = 'NOT_CLASSIFIED';
      } else if (upperClass === 'OTHER_RECOGNIZED_CLASSIFICATION' || upperClass === 'OTHER') {
        classification = 'OTHER';
      } else if (upperClass === 'HERITAGE') {
        classification = 'OTHER';
        if (!customClass) customClass = 'Heritage Hotel';
      }

      this.step1Data = {
        id: draft.id || null,
        name: draft.name || '',
        propertyType: draft.propertyType || '',
        propertyClassification: classification,
        customClassification: customClass,
        propertySegment: segment,
        brandName: draft.brandName || '',
        description: draft.description || ''
      };"""

assert old_draft_init in code, "old_draft_init not found"
code = code.replace(old_draft_init, new_draft_init, 1)

old_img_validation = """    // Validate format: JPG, JPEG, PNG, WebP
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type.toLowerCase())) {
      this.imageUploadErrors[slotIndex] = 'Unsupported format. Allowed formats are JPG, JPEG, PNG, or WebP.';
      event.target.value = '';
      return;
    }"""

new_img_validation = """    // Validate format: JPG, JPEG, PNG, WebP
    const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/pjpeg', 'image/png', 'image/webp'];
    const fileName = (file.name || '').toLowerCase();
    const hasAllowedExt = fileName.endsWith('.jpg') || fileName.endsWith('.jpeg') || fileName.endsWith('.png') || fileName.endsWith('.webp');
    const isMimeOk = file.type && allowedMimeTypes.includes(file.type.toLowerCase());

    if (!isMimeOk && !hasAllowedExt) {
      this.imageUploadErrors[slotIndex] = 'Unsupported format. Allowed formats are JPG, JPEG, PNG, or WebP.';
      event.target.value = '';
      return;
    }"""

assert old_img_validation in code, "old_img_validation not found"
code = code.replace(old_img_validation, new_img_validation, 1)

with open(ts_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Updated hotel-manager-dashboard.component.ts successfully!")
