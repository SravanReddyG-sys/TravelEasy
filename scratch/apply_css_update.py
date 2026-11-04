css_path = 'frontend/src/app/components/admin-dashboard/admin-dashboard.component.css'
with open(css_path, 'r', encoding='utf-8') as f:
    css = f.read()

start_target = '/* Base Layout Shell */'
end_target = '/* ==========================================================================\n   Dashboard Content Body\n   ========================================================================== */'

start_pos = css.find(start_target)
end_pos = css.find(end_target)

assert start_pos != -1, "start_target not found"
assert end_pos != -1, "end_target not found"

new_section = """/* Base Layout Shell */
.admin-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* ==========================================================================
   Top Header (Exact match with Bus Operator Dashboard)
   ========================================================================== */
.shell-header {
  height: 70px;
  background-color: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  position: sticky;
  top: 0;
  z-index: 1020;
}

.te-brand {
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.te-logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%);
  color: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.25);
}

.te-brand-title {
  font-size: 21px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.5px;
}

.te-brand-blue {
  color: #0284c7;
}

.operator-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7 0%, #0ea5e9 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  border: 2px solid #e0f2fe;
}

.hover-bg:hover {
  background-color: #f1f5f9;
}

.cursor-pointer {
  cursor: pointer;
}

/* ==========================================================================
   Shell Body & Sidebar Navigation
   ========================================================================== */
.admin-shell-body {
  display: flex;
  flex: 1;
  min-height: calc(100vh - 70px);
}

.admin-sidebar {
  width: 240px;
  background-color: #ffffff;
  border-right: 1px solid #e2e8f0;
  transition: width 0.2s ease, transform 0.25s ease;
  flex-shrink: 0;
  position: sticky;
  top: 70px;
  height: calc(100vh - 70px);
  display: flex;
  flex-direction: column;
  z-index: 1010;
  overflow-y: auto;
}

.admin-sidebar.collapsed {
  width: 76px;
}

.nav-link-custom {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  color: #64748b;
  font-weight: 500;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 3px;
  border: none;
  background: transparent;
  width: 100%;
  text-align: left;
}

.nav-link-custom:hover {
  background-color: #f8fafc;
  color: #0f172a;
}

.nav-link-custom.active {
  background-color: #e0f2fe;
  color: #0284c7;
  font-weight: 600;
}

.nav-link-custom.active i {
  color: #0284c7;
}

.sidebar-icon {
  font-size: 17px;
  width: 22px;
  text-align: center;
  flex-shrink: 0;
}

.sidebar-footer {
  padding: 16px 12px;
}

/* Mobile Sidebar Drawer */
.sidebar-backdrop {
  position: fixed;
  inset: 0;
  background-color: rgba(15, 23, 42, 0.45);
  z-index: 1015;
  backdrop-filter: blur(2px);
}

/* Dropdown Menus */
.notifications-dropdown,
.dropdown-menu-custom {
  position: absolute;
  top: 50px;
  right: 0;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  z-index: 1050;
  animation: fadeInDown 0.18s ease-out;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

"""

updated_css = css[:start_pos] + new_section + css[end_pos:]

# Update media query for max-width: 768px
old_mq = """.admin-sidebar {
    position: fixed;
    transform: translateX(-100%);
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  }
  .admin-sidebar.mobile-open {
    transform: translateX(0);
  }
  .admin-top-nav {
    padding: 0 16px;
  }"""

new_mq = """.admin-sidebar {
    position: fixed;
    top: 70px;
    height: calc(100vh - 70px);
    transform: translateX(-100%);
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  }
  .admin-sidebar.mobile-open {
    transform: translateX(0);
  }
  .shell-header {
    padding-left: 16px !important;
    padding-right: 16px !important;
  }"""

if old_mq in updated_css:
    updated_css = updated_css.replace(old_mq, new_mq, 1)

with open(css_path, 'w', encoding='utf-8') as f:
    f.write(updated_css)

print("admin-dashboard.component.css updated successfully!")
