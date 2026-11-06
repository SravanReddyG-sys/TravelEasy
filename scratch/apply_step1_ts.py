import re

# =========================================================================
# 1. UPDATE hotel-manager-dashboard.component.ts
# =========================================================================
ts_path = r"c:\Users\reddy\Documents\Materials_and_Assignments\FALL SEM(26-27)\SAA\SAA_Project\frontend\src\app\components\hotel-manager-dashboard\hotel-manager-dashboard.component.ts"
with open(ts_path, "r", encoding="utf-8") as f:
    ts_code = f.read()

# Import HotelImage, HotelStep1Request, HotelStep1Response
if 'HotelImage,' not in ts_code:
    ts_code = ts_code.replace("import { Hotel, HotelRoom } from '../../models/hotel.model';",
                              "import { Hotel, HotelRoom, HotelImage, HotelStep1Request, HotelStep1Response } from '../../models/hotel.model';")

# Add wizard state fields right under activeNav
wizard_fields = """  activeNav: string = 'dashboard';
  isSidebarCollapsed: boolean = false;
  isMobileSidebarOpen: boolean = false;

  // ==================== PROPERTY REGISTRATION WIZARD STATE (WP-05 STEP 1) ====================
  propertyViewMode: 'list' | 'wizard' = 'list';
  currentWizardStep: number = 1;
  managerDrafts: any[] = [];

  step1Data = {
    id: null as number | null,
    name: '',
    propertyType: '',
    propertyClassification: '',
    customClassification: '',
    propertySegment: '',
    brandName: '',
    description: ''
  };

  wizardImages: (HotelImage | null)[] = [null, null, null, null];
  imageUploadErrors: string[] = ['', '', '', ''];
  imageUploading: boolean[] = [false, false, false, false];

  step1Submitted: boolean = false;
  step1Submitting: boolean = false;
  step1ErrorMessage: string | null = null;
  step1SuccessMessage: string | null = null;
  isStep1Dirty: boolean = false;
  showCancelWizardModal: boolean = false;"""

target_nav = """  activeNav: string = 'dashboard';
  isSidebarCollapsed: boolean = false;
  isMobileSidebarOpen: boolean = false;"""

assert target_nav in ts_code, "target_nav not found in ts"
ts_code = ts_code.replace(target_nav, wizard_fields, 1)

# Add wizard methods before the end of the class
wizard_methods = """
  // ==================== PROPERTY REGISTRATION WIZARD METHODS (WP-05 STEP 1) ====================

  startPropertyRegistration(draft?: any): void {
    this.propertyViewMode = 'wizard';
    this.currentWizardStep = 1;
    this.step1Submitted = false;
    this.step1Submitting = false;
    this.step1ErrorMessage = null;
    this.step1SuccessMessage = null;
    this.isStep1Dirty = false;
    this.imageUploadErrors = ['', '', '', ''];
    this.imageUploading = [false, false, false, false];

    if (draft) {
      this.step1Data = {
        id: draft.id || null,
        name: draft.name || '',
        propertyType: draft.propertyType || '',
        propertyClassification: draft.propertyClassification || '',
        customClassification: draft.customClassification || '',
        propertySegment: draft.propertySegment || '',
        brandName: draft.brandName || '',
        description: draft.description || ''
      };
      this.wizardImages = [null, null, null, null];
      if (draft.images && Array.isArray(draft.images)) {
        draft.images.forEach((img: any, idx: number) => {
          if (idx < 4) {
            this.wizardImages[idx] = {
              id: img.id,
              imageUrl: img.imageUrl,
              originalFileName: img.originalFileName,
              isCover: img.isCover !== undefined ? img.isCover : (idx === 0),
              displayOrder: img.displayOrder !== undefined ? img.displayOrder : idx
            };
          }
        });
      }
    } else {
      this.step1Data = {
        id: null,
        name: '',
        propertyType: '',
        propertyClassification: '',
        customClassification: '',
        propertySegment: '',
        brandName: '',
        description: ''
      };
      this.wizardImages = [null, null, null, null];
    }
  }

  loadManagerDrafts(): void {
    this.hotelService.getManagerDrafts().subscribe({
      next: (drafts) => {
        this.managerDrafts = drafts || [];
      },
      error: (err) => console.warn('Failed to load drafts:', err)
    });
  }

  onStep1FieldChange(): void {
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
  }

  onClassificationChange(): void {
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
    if (this.step1Data.propertyClassification !== 'OTHER') {
      this.step1Data.customClassification = '';
    }
  }

  onDescriptionInput(event: any): void {
    const val: string = event.target.value || '';
    if (val.length > 300) {
      this.step1Data.description = val.substring(0, 300);
    }
    this.isStep1Dirty = true;
  }

  isPropertyNameValid(): boolean {
    return !!this.step1Data.name && this.step1Data.name.trim().length > 0 && this.step1Data.name.trim().length <= 255;
  }

  isPropertyTypeValid(): boolean {
    return !!this.step1Data.propertyType && this.step1Data.propertyType.trim().length > 0;
  }

  isClassificationValid(): boolean {
    if (this.step1Data.propertyClassification === 'OTHER') {
      return !!this.step1Data.customClassification && this.step1Data.customClassification.trim().length > 0;
    }
    return true;
  }

  isPropertySegmentValid(): boolean {
    return !!this.step1Data.propertySegment && this.step1Data.propertySegment.trim().length > 0;
  }

  isDescriptionValid(): boolean {
    return !!this.step1Data.description && this.step1Data.description.trim().length > 0 && this.step1Data.description.trim().length <= 300;
  }

  isCoverImageValid(): boolean {
    return this.wizardImages.some(img => img !== null && img.isCover && !!(img.imageUrl || img.previewUrl));
  }

  isStep1FormValid(): boolean {
    return this.isPropertyNameValid() &&
           this.isPropertyTypeValid() &&
           this.isClassificationValid() &&
           this.isPropertySegmentValid() &&
           this.isDescriptionValid() &&
           this.isCoverImageValid();
  }

  getUploadedImagesCount(): number {
    return this.wizardImages.filter(img => img !== null && (!!img.imageUrl || !!img.previewUrl)).length;
  }

  onImageFileSelected(event: any, slotIndex: number): void {
    const file = event.target.files?.[0];
    if (!file) return;

    this.imageUploadErrors[slotIndex] = '';

    // Validate size <= 1 MB (1,048,576 bytes)
    if (file.size > 1048576) {
      const sizeMb = (file.size / (1024 * 1024)).toFixed(1);
      this.imageUploadErrors[slotIndex] = `File exceeds 1 MB limit (${sizeMb} MB). Please choose a smaller image.`;
      event.target.value = '';
      return;
    }

    // Validate format: JPG, JPEG, PNG, WebP
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type.toLowerCase())) {
      this.imageUploadErrors[slotIndex] = 'Unsupported format. Allowed formats are JPG, JPEG, PNG, or WebP.';
      event.target.value = '';
      return;
    }

    // Local preview reader for immediate UX
    const reader = new FileReader();
    reader.onload = (e: any) => {
      const previewUrl = e.target.result;
      const isCover = slotIndex === 0 || !this.wizardImages.some(img => img !== null && img.isCover);

      this.wizardImages[slotIndex] = {
        imageUrl: '',
        previewUrl: previewUrl,
        originalFileName: file.name,
        isCover: isCover,
        displayOrder: slotIndex,
        file: file,
        uploading: true
      };
      this.isStep1Dirty = true;

      // Upload to backend
      this.imageUploading[slotIndex] = true;
      this.hotelService.uploadHotelImage(file, isCover ? 'cover' : 'gallery').subscribe({
        next: (res) => {
          this.imageUploading[slotIndex] = false;
          if (this.wizardImages[slotIndex]) {
            this.wizardImages[slotIndex]!.imageUrl = res.imageUrl;
            this.wizardImages[slotIndex]!.uploading = false;
          }
        },
        error: (err) => {
          this.imageUploading[slotIndex] = false;
          if (this.wizardImages[slotIndex]) {
            this.wizardImages[slotIndex]!.uploading = false;
          }
          this.imageUploadErrors[slotIndex] = err.error?.message || 'Failed to upload image. Please try again.';
        }
      });
    };
    reader.readAsDataURL(file);
    event.target.value = '';
  }

  removeImage(slotIndex: number, event: MouseEvent): void {
    event.stopPropagation();
    const img = this.wizardImages[slotIndex];
    if (img && img.imageUrl) {
      const filename = img.imageUrl.substring(img.imageUrl.lastIndexOf('/') + 1);
      this.hotelService.deleteHotelImage(filename).subscribe({
        next: () => {},
        error: (err) => console.warn('Failed to delete image from server:', err)
      });
    }

    const wasCover = img?.isCover;
    this.wizardImages[slotIndex] = null;
    this.imageUploadErrors[slotIndex] = '';
    this.isStep1Dirty = true;

    // If removed image was cover, assign cover to the first available image
    if (wasCover) {
      const nextImg = this.wizardImages.find(i => i !== null);
      if (nextImg) {
        nextImg.isCover = true;
      }
    }
  }

  setAsCover(slotIndex: number, event: MouseEvent): void {
    event.stopPropagation();
    const current = this.wizardImages[slotIndex];
    if (!current) return;

    this.wizardImages.forEach(img => {
      if (img) img.isCover = false;
    });
    current.isCover = true;
    this.isStep1Dirty = true;
  }

  proceedToStep2(): void {
    this.step1Submitted = true;
    this.step1ErrorMessage = null;

    if (!this.isStep1FormValid()) {
      if (!this.isCoverImageValid()) {
        this.step1ErrorMessage = 'Please upload a mandatory cover image (Tile 1) to proceed.';
      } else {
        this.step1ErrorMessage = 'Please complete all required fields correctly.';
      }
      return;
    }

    // Check if any image is still uploading
    if (this.imageUploading.some(u => u)) {
      this.step1ErrorMessage = 'Please wait for all images to finish uploading.';
      return;
    }

    this.step1Submitting = true;

    const imagesToSave = this.wizardImages
      .filter(img => img !== null && (!!img.imageUrl || !!img.previewUrl))
      .map((img, idx) => ({
        imageUrl: img!.imageUrl || img!.previewUrl || '',
        originalFileName: img!.originalFileName,
        isCover: img!.isCover,
        displayOrder: idx
      }));

    const payload: HotelStep1Request = {
      id: this.step1Data.id,
      name: this.step1Data.name.trim(),
      propertyType: this.step1Data.propertyType.trim(),
      propertyClassification: this.step1Data.propertyClassification || null,
      customClassification: this.step1Data.propertyClassification === 'OTHER' ? this.step1Data.customClassification.trim() : null,
      propertySegment: this.step1Data.propertySegment.trim(),
      brandName: this.step1Data.brandName ? this.step1Data.brandName.trim() : null,
      description: this.step1Data.description.trim(),
      images: imagesToSave
    };

    this.hotelService.saveStep1Draft(payload).subscribe({
      next: (res) => {
        this.step1Submitting = false;
        this.step1Data.id = res.id;
        this.isStep1Dirty = false;
        this.currentWizardStep = 2; // Advance to Step 2 placeholder
      },
      error: (err) => {
        this.step1Submitting = false;
        console.error('Save Step 1 draft error:', err);
        this.step1ErrorMessage = err.error?.message || 'Failed to save property draft. Please check your entries and try again.';
      }
    });
  }

  backToStep1(): void {
    this.currentWizardStep = 1;
  }

  confirmCancelWizard(): void {
    if (this.isStep1Dirty) {
      this.showCancelWizardModal = true;
    } else {
      this.propertyViewMode = 'list';
    }
  }

  discardAndExitWizard(): void {
    this.showCancelWizardModal = false;
    this.propertyViewMode = 'list';
    this.loadInitialData();
  }

  saveDraftAndExitWizard(): void {
    if (this.step1Data.name && this.step1Data.name.trim().length > 0 && this.isCoverImageValid()) {
      this.proceedToStep2();
      this.showCancelWizardModal = false;
      this.propertyViewMode = 'list';
      this.loadInitialData();
    } else {
      this.showCancelWizardModal = false;
      this.propertyViewMode = 'list';
    }
  }
"""

last_brace_idx = ts_code.rfind('}')
ts_code = ts_code[:last_brace_idx] + wizard_methods + "\n}\n"

with open(ts_path, "w", encoding="utf-8") as f:
    f.write(ts_code)
print("Updated hotel-manager-dashboard.component.ts successfully!")
