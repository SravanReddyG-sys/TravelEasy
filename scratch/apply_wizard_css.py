css_to_append = """
/* ==========================================================================
   WP-05 STEP 1: HOTEL PROPERTY REGISTRATION WIZARD STYLES
   ========================================================================== */

.wizard-container {
  max-width: 1100px;
  margin: 0 auto;
}

.wizard-back-link {
  font-size: 13.5px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
}

.wizard-back-link:hover {
  color: #00a896;
  transform: translateX(-2px);
}

/* 5-Step Stepper Bar */
.wizard-stepper-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px 32px;
}

.wizard-stepper-track {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  max-width: 900px;
  margin: 0 auto;
}

.stepper-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  z-index: 2;
  cursor: default;
}

.stepper-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  background: #ffffff;
  color: #94a3b8;
  border: 2px solid #e2e8f0;
  transition: all 0.25s ease;
}

.stepper-node.active .stepper-circle {
  background: #00a896;
  color: #ffffff;
  border-color: #00a896;
  box-shadow: 0 0 0 4px rgba(0, 168, 150, 0.18);
}

.stepper-node.completed .stepper-circle {
  background: #00a896;
  color: #ffffff;
  border-color: #00a896;
}

.stepper-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #94a3b8;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.stepper-node.active .stepper-label {
  color: #00a896;
  font-weight: 700;
}

.stepper-node.completed .stepper-label {
  color: #1e293b;
}

.stepper-connector {
  flex: 1;
  height: 2px;
  background: #e2e8f0;
  margin: -24px 12px 0 12px;
  z-index: 1;
  transition: background 0.3s ease;
}

.stepper-connector.active {
  background: #00a896;
}

/* Wizard Step Form Card */
.wizard-step-card {
  background: #ffffff;
  border-radius: 16px;
}

.form-label-custom {
  font-size: 13.5px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 6px;
  display: block;
}

.required-asterisk {
  color: #ef4444;
  font-weight: bold;
}

/* Custom Input Groups with Inner Icon Box */
.custom-input-group {
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.custom-input-group:focus-within {
  border-color: #00a896;
  box-shadow: 0 0 0 3px rgba(0, 168, 150, 0.12);
}

.custom-input-group.has-error {
  border-color: #ef4444 !important;
  background: #fffafa;
}

.input-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  font-size: 14.5px;
  color: #64748b;
  flex-shrink: 0;
}

.custom-form-input,
.custom-form-select {
  border: none;
  outline: none;
  width: 100%;
  height: 44px;
  padding: 0 14px;
  font-size: 13.5px;
  color: #1e293b;
  background: transparent;
}

.custom-form-select {
  cursor: pointer;
  appearance: auto;
}

.custom-textarea-group {
  display: flex;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.custom-textarea-group:focus-within {
  border-color: #00a896;
  box-shadow: 0 0 0 3px rgba(0, 168, 150, 0.12);
}

.custom-textarea-group.has-error {
  border-color: #ef4444 !important;
  background: #fffafa;
}

.textarea-icon-box {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  width: 44px;
  padding-top: 14px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  font-size: 14.5px;
  color: #64748b;
  flex-shrink: 0;
}

.custom-form-textarea {
  border: none;
  outline: none;
  width: 100%;
  padding: 12px 14px;
  font-size: 13.5px;
  color: #1e293b;
  background: transparent;
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

.field-error-text {
  font-size: 11.5px;
  color: #ef4444;
  margin-top: 4px;
  font-weight: 500;
}

.description-counter {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

/* Image Upload Section & Tiles */
.image-count-badge {
  background: #e6f7f5;
  color: #00a896;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 14px;
  border-radius: 20px;
  border: 1px solid rgba(0, 168, 150, 0.25);
}

.image-tile {
  height: 145px;
  border-radius: 14px;
  position: relative;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  transition: all 0.2s ease;
  user-select: none;
}

.cover-tile {
  border: 2px dashed #00a896;
  background: #f0faf9;
}

.cover-tile:hover {
  background: #e6f7f5;
  border-color: #008f80;
}

.optional-tile {
  border: 2px dashed #cbd5e1;
  background: #fafbfc;
}

.optional-tile:hover {
  border-color: #94a3b8;
  background: #f1f5f9;
}

.cover-tile.tile-error {
  border-color: #ef4444 !important;
  background: #fef2f2 !important;
}

.tile-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.cover-icon-wrap i {
  color: #00a896;
}

.tile-plus-badge {
  position: absolute;
  bottom: -4px;
  right: -8px;
  font-size: 10px;
  background: #ffffff;
  border-radius: 50%;
  padding: 1px;
}

.tile-main-text {
  font-size: 12.5px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 2px;
}

.tile-sub-text {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
}

.uploaded-tile-content {
  width: 100%;
  height: 100%;
  position: relative;
}

.tile-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-pill-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  background: #00a896;
  color: #ffffff;
  font-size: 10.5px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
  z-index: 2;
}

.tile-overlay-actions {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
  z-index: 3;
}

.uploaded-tile-content:hover .tile-overlay-actions {
  opacity: 1;
}

.tile-action-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
}

.tile-uploading-spinner {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 4;
}

.slot-error-text {
  font-size: 11px;
  color: #ef4444;
  margin-top: 4px;
  font-weight: 500;
}

/* Wizard Action Buttons */
.btn-wizard-cancel {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #475569;
  font-weight: 600;
  font-size: 13.5px;
  padding: 10px 26px;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.btn-wizard-cancel:hover {
  background: #f8fafc;
  color: #0f172a;
  border-color: #94a3b8;
}

.btn-wizard-next {
  background: #00a896;
  border: 1px solid #00a896;
  color: #ffffff;
  font-weight: 700;
  font-size: 13.5px;
  padding: 10px 28px;
  border-radius: 10px;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(0, 168, 150, 0.25);
}

.btn-wizard-next:hover {
  background: #008f80;
  border-color: #008f80;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 168, 150, 0.35);
}

.btn-wizard-next:disabled {
  background: #94a3b8;
  border-color: #94a3b8;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.btn-teal-primary {
  background: #00a896 !important;
  border-color: #00a896 !important;
  color: #ffffff !important;
}

.btn-teal-primary:hover {
  background: #008f80 !important;
  border-color: #008f80 !important;
}

.btn-outline-teal {
  background: transparent;
  border: 1px solid #00a896;
  color: #00a896;
}

.btn-outline-teal:hover {
  background: #00a896;
  color: #ffffff;
}

.text-teal {
  color: #00a896 !important;
}

.bg-light-cyan {
  background: #f0faf9 !important;
}

.draft-badge-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #e6f7f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step2-placeholder-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #e6f7f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Modal Backdrop & Dialog */
.modal-backdrop-custom {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(2px);
  z-index: 1060;
}

.modal-dialog-custom {
  position: relative;
  z-index: 1061;
}

@media (max-width: 767.98px) {
  .wizard-stepper-track {
    overflow-x: auto;
    padding-bottom: 8px;
  }

  .stepper-label {
    font-size: 11px;
  }
}
"""

css_path = r"frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.css"
with open(css_path, "r", encoding="utf-8") as f:
    css_content = f.read()

if "WP-05 STEP 1: HOTEL PROPERTY REGISTRATION WIZARD STYLES" not in css_content:
    with open(css_path, "a", encoding="utf-8") as f:
        f.write(css_to_append)
    print("Appended wizard styles to hotel-manager-dashboard.component.css!")
else:
    print("Styles already present.")
