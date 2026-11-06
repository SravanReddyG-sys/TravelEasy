# =========================================================================
# UPDATE hotel-manager-dashboard.component.html
# =========================================================================
html_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html"
with open(html_path, "r", encoding="utf-8") as f:
    html = f.read()

target_prop_start = "      <!-- ==================== TAB: MY PROPERTIES ==================== -->"
if target_prop_start not in html:
    # check for <div *ngIf="activeNav === 'properties'">
    target_prop_start = '<div *ngIf="activeNav === \'properties\'">'

prop_replacement = """      <!-- ==================== TAB: MY PROPERTIES ==================== -->
      <div *ngIf="activeNav === 'properties'">

        <!-- 1. PROPERTIES LIST VIEW -->
        <div *ngIf="propertyViewMode === 'list'">
          <div class="d-flex justify-content-between align-items-center mb-4">
            <div>
              <h3 class="fw-bold text-dark mb-1">My Hotel Properties</h3>
              <p class="text-muted small mb-0">Manage registered hotels, addresses, classifications, and amenities.</p>
            </div>
            <button class="btn btn-teal-primary text-white rounded-pill fw-bold px-3 py-2" (click)="startPropertyRegistration()">
              <i class="fa-solid fa-plus me-1"></i> Register New Property
            </button>
          </div>

          <!-- Pending Drafts Banner if any -->
          <div *ngIf="managerDrafts && managerDrafts.length > 0" class="card border-0 shadow-sm rounded-4 mb-4 p-3 bg-light-cyan">
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2">
              <div class="d-flex align-items-center gap-3">
                <div class="draft-badge-icon">
                  <i class="fa-solid fa-file-pen text-teal fs-5"></i>
                </div>
                <div>
                  <h6 class="fw-bold text-dark mb-0">Draft Property Registration in Progress</h6>
                  <p class="text-muted small mb-0">
                    You have {{ managerDrafts.length }} unfinished property registration(s).
                    <span *ngIf="managerDrafts[0]?.name">Latest: <strong>{{ managerDrafts[0].name }}</strong> (Step {{ managerDrafts[0].registrationStep || 1 }})</span>
                  </p>
                </div>
              </div>
              <button class="btn btn-outline-teal rounded-pill btn-sm fw-bold px-3" (click)="startPropertyRegistration(managerDrafts[0])">
                Resume Registration <i class="fa-solid fa-arrow-right ms-1"></i>
              </button>
            </div>
          </div>

          <!-- Property Cards Grid -->
          <div class="row g-4" *ngIf="myHotels.length > 0; else noHotelsRegistered">
            <div *ngFor="let h of myHotels" class="col-md-6 col-xl-4">
              <div class="card shadow-sm h-100 rounded-4 overflow-hidden border-0">
                <img [src]="h.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945'" 
                     class="card-img-top" 
                     style="height: 180px; object-fit: cover;">
                <div class="card-body p-4 d-flex flex-column justify-content-between">
                  <div>
                    <div class="d-flex justify-content-between align-items-start mb-2">
                      <h5 class="fw-bold mb-0 text-dark">{{ h.name }}</h5>
                      <span class="badge bg-warning text-dark"><i class="fa-solid fa-star me-1"></i>{{ h.starRating }}★</span>
                    </div>
                    <p class="text-muted small mb-2"><i class="fa-solid fa-location-dot me-1 text-danger"></i> {{ h.address }}, {{ h.city }}</p>
                    <p class="small text-secondary mb-3">{{ h.description || 'No description provided.' }}</p>
                    <div class="mb-3">
                      <span class="badge bg-light text-secondary border me-1 mb-1" *ngFor="let a of (h.amenities || '').split(',')">{{ a.trim() }}</span>
                    </div>
                  </div>
                  <div class="d-flex gap-2 mt-auto">
                    <button class="btn btn-outline-info btn-sm rounded-pill flex-grow-1 fw-bold" (click)="selectHotel(h); activeNav = 'rooms'">
                      Manage Rooms
                    </button>
                    <button class="btn btn-info btn-sm text-white rounded-pill flex-grow-1 fw-bold" (click)="openAddRoomModal(h)">
                      + Add Room
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <ng-template #noHotelsRegistered>
            <div class="card border-0 shadow-sm rounded-4 text-center p-5 bg-white">
              <div class="mb-3">
                <i class="fa-solid fa-hotel text-muted" style="font-size: 48px; opacity: 0.4;"></i>
              </div>
              <h5 class="fw-bold text-dark">No Hotel Properties Registered Yet</h5>
              <p class="text-muted small mx-auto" style="max-width: 420px;">
                Add your hotel to TravelEasy to begin managing room categories, inventory, and bookings.
              </p>
              <div class="mt-2">
                <button class="btn btn-teal-primary text-white rounded-pill fw-bold px-4 py-2" (click)="startPropertyRegistration()">
                  <i class="fa-solid fa-plus me-1"></i> Register New Property
                </button>
              </div>
            </div>
          </ng-template>
        </div>

        <!-- 2. PROPERTY REGISTRATION WIZARD VIEW (STEP 1) -->
        <div *ngIf="propertyViewMode === 'wizard'" class="wizard-container">

          <!-- Top Back Link -->
          <div class="mb-3">
            <a class="wizard-back-link text-decoration-none d-inline-flex align-items-center gap-2" (click)="confirmCancelWizard()">
              <i class="fa-solid fa-arrow-left"></i>
              <span>Back to My Properties</span>
            </a>
          </div>

          <!-- Wizard Page Header -->
          <div class="mb-4">
            <h2 class="fw-bold text-dark mb-1">Register New Property</h2>
            <p class="text-muted small mb-0">Add a new hotel property to your portfolio. Complete the following steps to register and activate your property.</p>
          </div>

          <!-- 5-Step Progress Stepper Bar -->
          <div class="wizard-stepper-card mb-4 bg-white rounded-4 border-0 shadow-sm p-4">
            <div class="wizard-stepper-track">
              <!-- Step 1 Node -->
              <div class="stepper-node" [class.active]="currentWizardStep === 1" [class.completed]="currentWizardStep > 1">
                <div class="stepper-circle">
                  <span *ngIf="currentWizardStep <= 1">1</span>
                  <i *ngIf="currentWizardStep > 1" class="fa-solid fa-check"></i>
                </div>
                <div class="stepper-label">Basic Details</div>
              </div>

              <div class="stepper-connector" [class.active]="currentWizardStep > 1"></div>

              <!-- Step 2 Node -->
              <div class="stepper-node" [class.active]="currentWizardStep === 2" [class.completed]="currentWizardStep > 2">
                <div class="stepper-circle">
                  <span *ngIf="currentWizardStep <= 2">2</span>
                  <i *ngIf="currentWizardStep > 2" class="fa-solid fa-check"></i>
                </div>
                <div class="stepper-label">Location & Address</div>
              </div>

              <div class="stepper-connector" [class.active]="currentWizardStep > 2"></div>

              <!-- Step 3 Node -->
              <div class="stepper-node" [class.active]="currentWizardStep === 3" [class.completed]="currentWizardStep > 3">
                <div class="stepper-circle">
                  <span *ngIf="currentWizardStep <= 3">3</span>
                  <i *ngIf="currentWizardStep > 3" class="fa-solid fa-check"></i>
                </div>
                <div class="stepper-label">Property Information</div>
              </div>

              <div class="stepper-connector" [class.active]="currentWizardStep > 3"></div>

              <!-- Step 4 Node -->
              <div class="stepper-node" [class.active]="currentWizardStep === 4" [class.completed]="currentWizardStep > 4">
                <div class="stepper-circle">
                  <span *ngIf="currentWizardStep <= 4">4</span>
                  <i *ngIf="currentWizardStep > 4" class="fa-solid fa-check"></i>
                </div>
                <div class="stepper-label">Facilities & Policies</div>
              </div>

              <div class="stepper-connector" [class.active]="currentWizardStep > 4"></div>

              <!-- Step 5 Node -->
              <div class="stepper-node" [class.active]="currentWizardStep === 5">
                <div class="stepper-circle">5</div>
                <div class="stepper-label">Review & Submit</div>
              </div>
            </div>
          </div>

          <!-- ==================== STEP 1 CONTENT ==================== -->
          <div *ngIf="currentWizardStep === 1" class="wizard-step-card bg-white rounded-4 border-0 shadow-sm p-4 p-md-5">

            <!-- Step Header -->
            <div class="border-bottom pb-3 mb-4">
              <h4 class="fw-bold text-dark mb-1">Step 1 of 5: Basic Property Details</h4>
              <p class="text-muted small mb-0">Provide the essential information about your property.</p>
            </div>

            <!-- Error Banner -->
            <div *ngIf="step1ErrorMessage" class="alert alert-danger d-flex align-items-center gap-2 rounded-3 py-2 px-3 mb-4" role="alert">
              <i class="fa-solid fa-circle-exclamation flex-shrink-0"></i>
              <div class="small fw-semibold">{{ step1ErrorMessage }}</div>
            </div>

            <!-- Form Content -->
            <form (ngSubmit)="proceedToStep2()" novalidate>
              <!-- Row 1: Property Name & Property Type -->
              <div class="row g-4 mb-4">
                <!-- Property Name -->
                <div class="col-md-6">
                  <label class="form-label-custom">
                    Property Name <span class="required-asterisk">*</span>
                  </label>
                  <div class="custom-input-group" [class.has-error]="step1Submitted && !isPropertyNameValid()">
                    <span class="input-icon-box">
                      <i class="fa-regular fa-building text-secondary"></i>
                    </span>
                    <input type="text" 
                           class="custom-form-input" 
                           placeholder="e.g., Sunrise Grand Hotel"
                           [(ngModel)]="step1Data.name"
                           name="propertyName"
                           (input)="onStep1FieldChange()"
                           required>
                  </div>
                  <div *ngIf="step1Submitted && !isPropertyNameValid()" class="field-error-text">
                    Property name is required.
                  </div>
                </div>

                <!-- Property Type -->
                <div class="col-md-6">
                  <label class="form-label-custom">
                    Property Type <span class="required-asterisk">*</span>
                  </label>
                  <div class="custom-input-group" [class.has-error]="step1Submitted && !isPropertyTypeValid()">
                    <span class="input-icon-box">
                      <i class="fa-solid fa-bed text-secondary"></i>
                    </span>
                    <select class="custom-form-select"
                            [(ngModel)]="step1Data.propertyType"
                            name="propertyType"
                            (change)="onStep1FieldChange()"
                            required>
                      <option value="" disabled selected>Select property type</option>
                      <option value="Hotel">Hotel</option>
                      <option value="Resort">Resort</option>
                      <option value="Villa">Villa</option>
                      <option value="Apartment">Apartment</option>
                      <option value="Boutique Hotel">Boutique Hotel</option>
                      <option value="Guest House">Guest House</option>
                      <option value="Hostel">Hostel</option>
                      <option value="Homestay">Homestay</option>
                      <option value="Lodge">Lodge</option>
                      <option value="Other">Other</option>
                    </select>
                  </div>
                  <div *ngIf="step1Submitted && !isPropertyTypeValid()" class="field-error-text">
                    Please select a property type.
                  </div>
                </div>
              </div>

              <!-- Row 2: Recognized Property Classification & Property Segment -->
              <div class="row g-4 mb-4">
                <!-- Recognized Property Classification -->
                <div class="col-md-6">
                  <label class="form-label-custom d-flex align-items-center gap-1">
                    <span>Recognized Property Classification</span>
                    <i class="fa-solid fa-circle-info text-muted ms-1 cursor-pointer" 
                       title="Select government or industry recognized star rating, heritage status, or select Not Classified." style="font-size: 13px;"></i>
                  </label>
                  <div class="custom-input-group">
                    <span class="input-icon-box">
                      <i class="fa-regular fa-star text-secondary"></i>
                    </span>
                    <select class="custom-form-select"
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
                    </select>
                  </div>

                  <!-- Conditional Custom Classification Input if OTHER -->
                  <div *ngIf="step1Data.propertyClassification === 'OTHER'" class="mt-2">
                    <div class="custom-input-group" [class.has-error]="step1Submitted && !isClassificationValid()">
                      <span class="input-icon-box">
                        <i class="fa-solid fa-pen-nib text-secondary"></i>
                      </span>
                      <input type="text"
                             class="custom-form-input"
                             placeholder="Specify custom classification (e.g. Eco Resort)"
                             [(ngModel)]="step1Data.customClassification"
                             name="customClassification"
                             (input)="onStep1FieldChange()"
                             required>
                    </div>
                    <div *ngIf="step1Submitted && !isClassificationValid()" class="field-error-text">
                      Please specify your custom classification.
                    </div>
                  </div>
                </div>

                <!-- Property Segment -->
                <div class="col-md-6">
                  <label class="form-label-custom">
                    Property Segment <span class="required-asterisk">*</span>
                  </label>
                  <div class="custom-input-group" [class.has-error]="step1Submitted && !isPropertySegmentValid()">
                    <span class="input-icon-box">
                      <i class="fa-solid fa-gem text-secondary"></i>
                    </span>
                    <select class="custom-form-select"
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
                    </select>
                  </div>
                  <div *ngIf="step1Submitted && !isPropertySegmentValid()" class="field-error-text">
                    Please select a property segment.
                  </div>
                </div>
              </div>

              <!-- Row 3: Brand Name (Optional) -->
              <div class="row g-4 mb-4">
                <div class="col-12">
                  <label class="form-label-custom">
                    Brand Name (Optional)
                  </label>
                  <div class="custom-input-group">
                    <span class="input-icon-box">
                      <i class="fa-solid fa-tag text-secondary"></i>
                    </span>
                    <input type="text"
                           class="custom-form-input"
                           placeholder="e.g., Sunrise Hotels"
                           [(ngModel)]="step1Data.brandName"
                           name="brandName"
                           (input)="onStep1FieldChange()">
                  </div>
                </div>
              </div>

              <!-- Row 4: Short Description -->
              <div class="row g-4 mb-4">
                <div class="col-12">
                  <label class="form-label-custom">
                    Short Description <span class="required-asterisk">*</span>
                  </label>
                  <div class="custom-textarea-group" [class.has-error]="step1Submitted && !isDescriptionValid()">
                    <span class="textarea-icon-box">
                      <i class="fa-regular fa-file-lines text-secondary"></i>
                    </span>
                    <textarea class="custom-form-textarea"
                              rows="3"
                              placeholder="Write a brief description of your property (max 300 characters)..."
                              [ngModel]="step1Data.description"
                              name="description"
                              maxlength="300"
                              (input)="onDescriptionInput($event)"
                              required></textarea>
                  </div>
                  <div class="d-flex justify-content-between align-items-center mt-1">
                    <div *ngIf="step1Submitted && !isDescriptionValid()" class="field-error-text">
                      Short description is required (up to 300 characters).
                    </div>
                    <div class="description-counter ms-auto text-muted small">
                      {{ step1Data.description?.length || 0 }}/300
                    </div>
                  </div>
                </div>
              </div>

              <!-- Row 5: Property Images Section -->
              <div class="property-images-section mb-4">
                <div class="d-flex justify-content-between align-items-start mb-2">
                  <div>
                    <label class="form-label-custom d-inline-flex align-items-center gap-1 mb-1">
                      <span>Property Images</span>
                      <span class="required-asterisk">*</span>
                      <i class="fa-solid fa-circle-info text-muted ms-1 cursor-pointer"
                         title="Add up to 4 high-resolution images. Tile 1 is the cover image displayed on search results."
                         style="font-size: 13px;"></i>
                    </label>
                    <p class="text-muted small mb-0">
                      Upload up to 4 images including a cover image. Each image must be ≤ 1 MB. (JPG, JPEG, PNG, WebP)
                    </p>
                  </div>
                  <div class="image-count-badge">
                    {{ getUploadedImagesCount() }} / 4 Images
                  </div>
                </div>

                <!-- 4 Image Tiles Grid -->
                <div class="row g-3 mt-1">
                  <!-- Hidden file inputs for each slot -->
                  <input type="file" #fileInput0 class="d-none" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" (change)="onImageFileSelected($event, 0)">
                  <input type="file" #fileInput1 class="d-none" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" (change)="onImageFileSelected($event, 1)">
                  <input type="file" #fileInput2 class="d-none" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" (change)="onImageFileSelected($event, 2)">
                  <input type="file" #fileInput3 class="d-none" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" (change)="onImageFileSelected($event, 3)">

                  <!-- Tile 1: Cover Image (Mandatory) -->
                  <div class="col-12 col-sm-6 col-md-3">
                    <div class="image-tile cover-tile" 
                         [class.has-image]="!!wizardImages[0]"
                         [class.tile-error]="step1Submitted && !wizardImages[0]"
                         (click)="!wizardImages[0] ? fileInput0.click() : null">
                      
                      <!-- Empty Tile State -->
                      <div *ngIf="!wizardImages[0]" class="empty-tile-content">
                        <div class="tile-icon-wrap cover-icon-wrap">
                          <i class="fa-regular fa-image fs-4 text-teal"></i>
                          <i class="fa-solid fa-plus tile-plus-badge text-teal"></i>
                        </div>
                        <div class="tile-main-text">Upload Cover Image <span class="required-asterisk">*</span></div>
                        <div class="tile-sub-text">Required • ≤ 1 MB</div>
                      </div>

                      <!-- Uploaded Image State -->
                      <div *ngIf="wizardImages[0]" class="uploaded-tile-content">
                        <img [src]="wizardImages[0]?.previewUrl || wizardImages[0]?.imageUrl" class="tile-img" alt="Cover Image">
                        <span class="cover-pill-badge">Cover Image</span>
                        <div class="tile-overlay-actions">
                          <button type="button" class="btn btn-sm btn-light rounded-circle tile-action-btn" (click)="fileInput0.click()" title="Replace image">
                            <i class="fa-solid fa-arrows-rotate"></i>
                          </button>
                          <button type="button" class="btn btn-sm btn-danger rounded-circle tile-action-btn" (click)="removeImage(0, $event)" title="Remove image">
                            <i class="fa-solid fa-trash-can"></i>
                          </button>
                        </div>
                        <div *ngIf="imageUploading[0]" class="tile-uploading-spinner">
                          <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                        </div>
                      </div>
                    </div>
                    <!-- Slot 0 Error Message -->
                    <div *ngIf="imageUploadErrors[0]" class="slot-error-text">{{ imageUploadErrors[0] }}</div>
                    <div *ngIf="step1Submitted && !wizardImages[0]" class="slot-error-text">Cover image is required.</div>
                  </div>

                  <!-- Tile 2: Optional Image -->
                  <div class="col-12 col-sm-6 col-md-3">
                    <div class="image-tile optional-tile" 
                         [class.has-image]="!!wizardImages[1]"
                         (click)="!wizardImages[1] ? fileInput1.click() : null">
                      
                      <div *ngIf="!wizardImages[1]" class="empty-tile-content">
                        <div class="tile-icon-wrap">
                          <i class="fa-solid fa-plus fs-4 text-muted"></i>
                        </div>
                        <div class="tile-main-text">Add Image</div>
                        <div class="tile-sub-text">Optional • ≤ 1 MB</div>
                      </div>

                      <div *ngIf="wizardImages[1]" class="uploaded-tile-content">
                        <img [src]="wizardImages[1]?.previewUrl || wizardImages[1]?.imageUrl" class="tile-img" alt="Gallery Image">
                        <span *ngIf="wizardImages[1]?.isCover" class="cover-pill-badge">Cover Image</span>
                        <div class="tile-overlay-actions">
                          <button *ngIf="!wizardImages[1]?.isCover" type="button" class="btn btn-sm btn-light tile-action-btn text-teal fw-bold" (click)="setAsCover(1, $event)" title="Make Cover">
                            Set Cover
                          </button>
                          <button type="button" class="btn btn-sm btn-light rounded-circle tile-action-btn" (click)="fileInput1.click()" title="Replace image">
                            <i class="fa-solid fa-arrows-rotate"></i>
                          </button>
                          <button type="button" class="btn btn-sm btn-danger rounded-circle tile-action-btn" (click)="removeImage(1, $event)" title="Remove image">
                            <i class="fa-solid fa-trash-can"></i>
                          </button>
                        </div>
                        <div *ngIf="imageUploading[1]" class="tile-uploading-spinner">
                          <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                        </div>
                      </div>
                    </div>
                    <div *ngIf="imageUploadErrors[1]" class="slot-error-text">{{ imageUploadErrors[1] }}</div>
                  </div>

                  <!-- Tile 3: Optional Image -->
                  <div class="col-12 col-sm-6 col-md-3">
                    <div class="image-tile optional-tile" 
                         [class.has-image]="!!wizardImages[2]"
                         (click)="!wizardImages[2] ? fileInput2.click() : null">
                      
                      <div *ngIf="!wizardImages[2]" class="empty-tile-content">
                        <div class="tile-icon-wrap">
                          <i class="fa-solid fa-plus fs-4 text-muted"></i>
                        </div>
                        <div class="tile-main-text">Add Image</div>
                        <div class="tile-sub-text">Optional • ≤ 1 MB</div>
                      </div>

                      <div *ngIf="wizardImages[2]" class="uploaded-tile-content">
                        <img [src]="wizardImages[2]?.previewUrl || wizardImages[2]?.imageUrl" class="tile-img" alt="Gallery Image">
                        <span *ngIf="wizardImages[2]?.isCover" class="cover-pill-badge">Cover Image</span>
                        <div class="tile-overlay-actions">
                          <button *ngIf="!wizardImages[2]?.isCover" type="button" class="btn btn-sm btn-light tile-action-btn text-teal fw-bold" (click)="setAsCover(2, $event)" title="Make Cover">
                            Set Cover
                          </button>
                          <button type="button" class="btn btn-sm btn-light rounded-circle tile-action-btn" (click)="fileInput2.click()" title="Replace image">
                            <i class="fa-solid fa-arrows-rotate"></i>
                          </button>
                          <button type="button" class="btn btn-sm btn-danger rounded-circle tile-action-btn" (click)="removeImage(2, $event)" title="Remove image">
                            <i class="fa-solid fa-trash-can"></i>
                          </button>
                        </div>
                        <div *ngIf="imageUploading[2]" class="tile-uploading-spinner">
                          <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                        </div>
                      </div>
                    </div>
                    <div *ngIf="imageUploadErrors[2]" class="slot-error-text">{{ imageUploadErrors[2] }}</div>
                  </div>

                  <!-- Tile 4: Optional Image -->
                  <div class="col-12 col-sm-6 col-md-3">
                    <div class="image-tile optional-tile" 
                         [class.has-image]="!!wizardImages[3]"
                         (click)="!wizardImages[3] ? fileInput3.click() : null">
                      
                      <div *ngIf="!wizardImages[3]" class="empty-tile-content">
                        <div class="tile-icon-wrap">
                          <i class="fa-solid fa-plus fs-4 text-muted"></i>
                        </div>
                        <div class="tile-main-text">Add Image</div>
                        <div class="tile-sub-text">Optional • ≤ 1 MB</div>
                      </div>

                      <div *ngIf="wizardImages[3]" class="uploaded-tile-content">
                        <img [src]="wizardImages[3]?.previewUrl || wizardImages[3]?.imageUrl" class="tile-img" alt="Gallery Image">
                        <span *ngIf="wizardImages[3]?.isCover" class="cover-pill-badge">Cover Image</span>
                        <div class="tile-overlay-actions">
                          <button *ngIf="!wizardImages[3]?.isCover" type="button" class="btn btn-sm btn-light tile-action-btn text-teal fw-bold" (click)="setAsCover(3, $event)" title="Make Cover">
                            Set Cover
                          </button>
                          <button type="button" class="btn btn-sm btn-light rounded-circle tile-action-btn" (click)="fileInput3.click()" title="Replace image">
                            <i class="fa-solid fa-arrows-rotate"></i>
                          </button>
                          <button type="button" class="btn btn-sm btn-danger rounded-circle tile-action-btn" (click)="removeImage(3, $event)" title="Remove image">
                            <i class="fa-solid fa-trash-can"></i>
                          </button>
                        </div>
                        <div *ngIf="imageUploading[3]" class="tile-uploading-spinner">
                          <div class="spinner-border spinner-border-sm text-white" role="status"></div>
                        </div>
                      </div>
                    </div>
                    <div *ngIf="imageUploadErrors[3]" class="slot-error-text">{{ imageUploadErrors[3] }}</div>
                  </div>
                </div>
              </div>

              <!-- Action Buttons -->
              <div class="d-flex justify-content-end align-items-center gap-3 pt-4 border-top">
                <button type="button" 
                        class="btn btn-wizard-cancel" 
                        (click)="confirmCancelWizard()">
                  Cancel
                </button>
                <button type="button" 
                        class="btn btn-wizard-next" 
                        [disabled]="step1Submitting"
                        (click)="proceedToStep2()">
                  <span *ngIf="!step1Submitting">Next: Location & Address &rarr;</span>
                  <span *ngIf="step1Submitting">
                    <span class="spinner-border spinner-border-sm me-1" role="status"></span>
                    Saving Draft...
                  </span>
                </button>
              </div>
            </form>
          </div>

          <!-- ==================== STEP 2 PLACEHOLDER ==================== -->
          <div *ngIf="currentWizardStep === 2" class="wizard-step-card bg-white rounded-4 border-0 shadow-sm p-4 p-md-5 text-center">
            <div class="my-4">
              <div class="step2-placeholder-icon mx-auto mb-3">
                <i class="fa-solid fa-map-location-dot text-teal" style="font-size: 40px;"></i>
              </div>
              <h4 class="fw-bold text-dark mb-2">Step 2: Location & Address</h4>
              <div class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 py-2 px-3 rounded-pill mb-3">
                <i class="fa-solid fa-circle-check me-1"></i> Draft Saved Successfully (Draft ID: #{{ step1Data.id }})
              </div>
              <p class="text-muted small mx-auto mb-4" style="max-width: 540px;">
                Your basic property details and uploaded images have been saved securely as a <strong>DRAFT</strong>.
                In accordance with WP-05 boundaries, Step 1 is complete. Steps 2 through 5 will be fully implemented in subsequent assignments.
              </p>
              <div class="d-flex justify-content-center gap-3">
                <button type="button" class="btn btn-outline-secondary rounded-3 px-4 fw-bold" (click)="backToStep1()">
                  &larr; Back to Basic Details
                </button>
                <button type="button" class="btn btn-teal-primary text-white rounded-3 px-4 fw-bold" (click)="propertyViewMode = 'list'; loadInitialData()">
                  Done for Now (Return to Properties)
                </button>
              </div>
            </div>
          </div>

          <!-- Cancel / Discard Confirmation Modal -->
          <div *ngIf="showCancelWizardModal" class="modal-backdrop-custom d-flex align-items-center justify-content-center">
            <div class="modal-dialog-custom bg-white rounded-4 shadow-lg p-4" style="max-width: 480px; width: 90%;">
              <div class="d-flex align-items-center gap-3 mb-3">
                <div class="modal-warning-icon rounded-circle bg-warning bg-opacity-10 text-warning d-flex align-items-center justify-content-center" style="width: 48px; height: 48px; font-size: 20px;">
                  <i class="fa-solid fa-triangle-exclamation"></i>
                </div>
                <div>
                  <h5 class="fw-bold text-dark mb-0">Unsaved Changes</h5>
                  <p class="text-muted small mb-0">You have unsaved changes in this registration draft.</p>
                </div>
              </div>
              <p class="text-secondary small mb-4">
                Would you like to save your draft before leaving, or discard all changes made in this session?
              </p>
              <div class="d-flex flex-column flex-sm-row justify-content-end gap-2">
                <button type="button" class="btn btn-light rounded-3 px-3 py-2 fw-semibold" (click)="showCancelWizardModal = false">
                  Continue Editing
                </button>
                <button type="button" class="btn btn-outline-danger rounded-3 px-3 py-2 fw-semibold" (click)="discardAndExitWizard()">
                  Discard Changes
                </button>
                <button type="button" class="btn btn-teal-primary text-white rounded-3 px-3 py-2 fw-semibold" (click)="saveDraftAndExitWizard()">
                  Save & Exit
                </button>
              </div>
            </div>
          </div>

        </div>

      </div>"""

# Replace the existing properties tab
lines = html.splitlines(True)
start_line = -1
end_line = -1
for i, line in enumerate(lines):
    if "activeNav === 'properties'" in line and "<div" in line:
        start_line = i
        for j in range(start_line, len(lines)):
            if "activeNav === 'rooms'" in lines[j]:
                end_line = j - 1
                break
        break

print(f"Replacing HTML lines {start_line+1} to {end_line+1}")
new_lines = lines[:start_line] + [prop_replacement + "\n\n"] + lines[end_line+1:]
new_html = "".join(new_lines)

with open(html_path, "w", encoding="utf-8") as f:
    f.write(new_html)
print("Updated hotel-manager-dashboard.component.html successfully!")
