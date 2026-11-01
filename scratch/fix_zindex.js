const fs = require('fs');
const path = require('path');

const filePath = path.resolve('frontend/src/app/components/bus-operator-dashboard/bus-operator-dashboard.component.ts');
let content = fs.readFileSync(filePath, 'utf8');

const isCrlf = content.includes('\r\n');
const eol = isCrlf ? '\r\n' : '\n';

// 1. Update row div in template
const oldRowDiv = '<div *ngFor="let bus of getPaginatedBuses()" class="card border-0 shadow-sm rounded-4 mb-3 p-3 my-buses-list-row position-relative">';
const newRowDiv = '<div *ngFor="let bus of getPaginatedBuses()"' + eol +
  '                     class="card border-0 shadow-sm rounded-4 mb-3 p-3 my-buses-list-row position-relative"' + eol +
  '                     [class.menu-open]="activeBusOverflowMenuId === bus.id"' + eol +
  '                     [style.z-index]="activeBusOverflowMenuId === bus.id ? 100 : \'auto\'">';

if (content.includes(oldRowDiv)) {
  content = content.replace(oldRowDiv, newRowDiv);
  console.log('1. Updated row div with menu-open and z-index.');
} else {
  console.warn('1. Warning: oldRowDiv not found.');
}

// 2. Update actions col and dropdown in template
const oldActionsCol = `                    <!-- Col 8: Actions (View Button + Three-Dots Overflow) -->
                    <div class="bus-row-actions-col d-flex align-items-center gap-2 flex-shrink-0 position-relative">
                      <!-- View Button -->
                      <button type="button"
                              class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold d-inline-flex align-items-center gap-2 my-buses-view-btn"
                              (click)="openBusDetails(bus)">
                        <i class="fa-regular fa-eye"></i>
                        <span>View</span>
                      </button>

                      <!-- Overflow Button -->
                      <div class="dropdown position-relative">`;

const newActionsCol = `                    <!-- Col 8: Actions (View Button + Three-Dots Overflow) -->
                    <div class="bus-row-actions-col d-flex align-items-center gap-2 flex-shrink-0 position-relative"
                         [style.z-index]="activeBusOverflowMenuId === bus.id ? 101 : 'auto'">
                      <!-- View Button -->
                      <button type="button"
                              class="btn btn-outline-primary btn-sm rounded-3 px-3 py-1 fw-semibold d-inline-flex align-items-center gap-2 my-buses-view-btn"
                              (click)="openBusDetails(bus)">
                        <i class="fa-regular fa-eye"></i>
                        <span>View</span>
                      </button>

                      <!-- Overflow Button -->
                      <div class="dropdown position-relative"
                           [style.z-index]="activeBusOverflowMenuId === bus.id ? 102 : 'auto'">`;

if (content.includes(oldActionsCol.replace(/\n/g, eol))) {
  content = content.replace(oldActionsCol.replace(/\n/g, eol), newActionsCol.replace(/\n/g, eol));
  console.log('2. Updated actions col and dropdown z-index.');
} else {
  console.warn('2. Warning: oldActionsCol not found.');
}

// 3. Update CSS for .my-buses-list-row and .my-buses-dropdown-menu
const oldRowCss = `    .my-buses-list-row {
      background: #ffffff;
      border: 1px solid #e2e8f0 !important;
      transition: all 0.2s ease;
    }
    .my-buses-list-row:hover {
      border-color: #cbd5e1 !important;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06) !important;
      transform: translateY(-1px);
    }`;

const newRowCss = `    .my-buses-list-row {
      background: #ffffff;
      border: 1px solid #e2e8f0 !important;
      transition: all 0.2s ease;
      position: relative;
    }
    .my-buses-list-row:hover {
      border-color: #cbd5e1 !important;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06) !important;
      transform: translateY(-1px);
    }
    .my-buses-list-row.menu-open {
      z-index: 100 !important;
      transform: none !important;
    }`;

if (content.includes(oldRowCss.replace(/\n/g, eol))) {
  content = content.replace(oldRowCss.replace(/\n/g, eol), newRowCss.replace(/\n/g, eol));
  console.log('3. Updated CSS for my-buses-list-row.');
} else {
  console.warn('3. Warning: oldRowCss not found.');
}

fs.writeFileSync(filePath, content, 'utf8');
console.log('Successfully applied z-index overlay fix!');
