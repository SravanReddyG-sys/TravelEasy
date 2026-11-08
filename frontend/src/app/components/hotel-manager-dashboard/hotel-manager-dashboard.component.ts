import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { HotelService } from '../../services/hotel.service';
import { Hotel, HotelRoom, HotelImage, HotelStep1Request, HotelStep1Response, HotelStep2Request, HotelStep2Response, HotelStep3Request, HotelStep3Response, HotelStep4Request, HotelStep4Response, HotelStep5SubmitRequest, HotelStep5Response, RoomTypeCreateRequest, RoomTypeDto } from '../../models/hotel.model';
import { BookingService, HotelRevenueReportResponse } from '../../services/booking.service';
import { NotificationService } from '../../services/notification.service';
import { User } from '../../models/user.model';
import { FormattedNotification } from '../../models/notification.model';

export interface DateRangePresetItem {
  id: string;
  label: string;
}

export interface KpiComparison {
  currentValue: number;
  previousValue: number;
  diff: number;
  percentageChange: number | null;
  isPositive: boolean;
  isNegative: boolean;
  isNeutral: boolean;
  trendText: string;
  hasData: boolean;
}

export interface TrendDayItem {
  dateLabel: string;
  fullDate: string;
  count: number;
  revenue: number;
  heightPx: number;
  revenueHeightPx: number;
  heightPercent: number;
  revenueHeightPercent: number;
  showLabel: boolean;
}

@Component({
  selector: 'app-hotel-manager-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './hotel-manager-dashboard.component.html',
  styleUrls: ['./hotel-manager-dashboard.component.css']
})
export class HotelManagerDashboardComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  activeNav: string = 'dashboard';
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
  showCancelWizardModal: boolean = false;

  // Step 2 State (Location & Address)
  step2Data = {
    addressLine1: '',
    addressLine2: '',
    country: 'India',
    state: '',
    city: '',
    postalCode: '',
    landmark: ''
  };

  step2Submitted: boolean = false;
  step2Submitting: boolean = false;
  step2ErrorMessage: string | null = null;
  isStep2Dirty: boolean = false;

  // Step 3 State (Property Information)
  step3Data = {
    // 1. Contact
    propertyContactNumber: '',
    alternateContactNumber: '',
    propertyEmail: '',
    websiteUrl: '',
    // 2. Business Registration
    registeredBusinessName: '',
    businessRegistrationType: 'Private Limited Company',
    otherBusinessRegistrationType: '',
    businessRegistrationNumber: '',
    registeredBusinessAddress: '',
    // 3. GST Details
    isGstRegistered: false,
    gstin: '',
    gstLegalName: '',
    gstState: '',
    // 4. Property Manager Details
    propertyManagerName: '',
    propertyManagerDesignation: 'General Manager',
    propertyManagerContactNumber: '',
    propertyManagerEmail: '',
    // 5. Operating Hours
    checkInTime: '14:00',
    checkOutTime: '11:00',
    receptionAvailability: '24-hour Reception',
    receptionOpeningTime: '',
    receptionClosingTime: ''
  };

  step3Submitted: boolean = false;
  step3Submitting: boolean = false;
  step3ErrorMessage: string | null = null;
  isStep3Dirty: boolean = false;

  // Accordion active subsection ('contact', 'business', 'gst', 'manager', 'operating')
  expandedStep3Section: string = 'contact';

  // ==================== STEP 4: FACILITIES & POLICIES ====================
  step4Data = {
    predefinedAmenities: [] as string[],
    customAmenities: [] as string[],
    childrenPolicy: 'Children Allowed',
    petPolicy: 'Pets Not Allowed',
    smokingPolicy: 'Smoking Prohibited',
    extraBedPolicy: 'Extra Bed Available',
    acceptedIdentificationTypes: ['Passport', 'Aadhaar Card'] as string[],
    customIdentificationType: '',
    additionalRules: ''
  };

  expandedStep4Section: 'amenities' | 'policies' | 'rules' | null = 'amenities';
  step4Submitted: boolean = false;
  step4Submitting: boolean = false;
  step4ErrorMessage: string | null = null;
  isStep4Dirty: boolean = false;

  showCustomAmenityInput: boolean = false;
  newCustomAmenityName: string = '';
  customAmenityError: string = '';

  predefinedAmenityCategories = [
    {
      category: 'Connectivity',
      icon: 'fa-solid fa-wifi',
      amenities: ['Wi-Fi', 'Business Centre'],
      fullWidth: false
    },
    {
      category: 'Dining',
      icon: 'fa-solid fa-utensils',
      amenities: ['Restaurant', 'Room Service', 'Complimentary Breakfast'],
      fullWidth: false
    },
    {
      category: 'Transport',
      icon: 'fa-solid fa-car',
      amenities: ['Parking', 'Airport Shuttle'],
      fullWidth: false
    },
    {
      category: 'Recreation',
      icon: 'fa-solid fa-water',
      amenities: ['Swimming Pool', 'Gym', 'Spa'],
      fullWidth: false
    },
    {
      category: 'Safety',
      icon: 'fa-solid fa-shield-halved',
      amenities: ['CCTV', '24-hour Security', 'Fire Safety'],
      fullWidth: false
    },
    {
      category: 'Accessibility',
      icon: 'fa-solid fa-wheelchair',
      amenities: ['Accessible Entrance', 'Elevator'],
      fullWidth: false
    },
    {
      category: 'General',
      icon: 'fa-solid fa-layer-group',
      amenities: ['Laundry', 'Reception', 'Luggage Storage'],
      fullWidth: true
    }
  ];

  childrenPolicyOptions: string[] = ['Children Allowed', 'Children Not Allowed'];
  petPolicyOptions: string[] = ['Pets Allowed', 'Pets Not Allowed'];
  smokingPolicyOptions: string[] = ['Smoking Prohibited', 'Designated Smoking Areas', 'Smoking Allowed'];
  extraBedPolicyOptions: string[] = ['Extra Bed Available', 'Extra Bed Not Available'];
  identificationTypeOptions: string[] = [
    'Passport',
    'Driving Licence',
    'Voter ID',
    'Government-issued Photo ID',
    'Aadhaar Card',
    'Other'
  ];

  // ==================== WP-05 STEP 5: REVIEW & SUBMIT STATE ====================
  reviewSectionsExpanded = {
    basic: true,
    address: false,
    propertyInfo: false,
    facilities: false
  };

  editingFromReview: boolean = false;
  accuracyConfirmed: boolean = false;
  step5Submitted: boolean = false;
  step5Submitting: boolean = false;
  step5ErrorMessage: string | null = null;
  registrationSuccess: boolean = false;
  activatedPropertyDetails: HotelStep5Response | null = null;
  showTermsModal: boolean = false;


  businessRegistrationTypes: string[] = [
    'Private Limited Company',
    'Public Limited Company',
    'Partnership',
    'Limited Liability Partnership (LLP)',
    'Sole Proprietorship',
    'Other'
  ];

  managerDesignations: string[] = [
    'General Manager',
    'Property Manager',
    'Operations Manager',
    'Hotel Administrator',
    'Owner / Proprietor',
    'Other'
  ];

  receptionAvailabilityOptions: string[] = [
    '24-hour Reception',
    'Limited Operating Hours'
  ];

  countries: string[] = [
    'India',
    'United States',
    'United Kingdom',
    'United Arab Emirates',
    'Australia',
    'Canada',
    'Singapore',
    'Germany',
    'France',
    'Japan',
    'Malaysia',
    'Thailand',
    'Saudi Arabia',
    'South Africa',
    'Italy',
    'Spain',
    'Netherlands',
    'Switzerland',
    'New Zealand'
  ];

  availableStates: string[] = [];

  countryStatesMap: { [country: string]: string[] } = {
    'India': [
      'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh',
      'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand',
      'Karnataka', 'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur',
      'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
      'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
      'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
      'Andaman and Nicobar Islands', 'Chandigarh',
      'Dadra and Nagar Haveli and Daman and Diu', 'Delhi',
      'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry'
    ],
    'United States': [
      'Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut',
      'Delaware', 'District of Columbia', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois',
      'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts',
      'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada',
      'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Carolina', 'North Dakota',
      'Ohio', 'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina',
      'South Dakota', 'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington',
      'West Virginia', 'Wisconsin', 'Wyoming'
    ],
    'United Kingdom': [
      'England', 'Scotland', 'Wales', 'Northern Ireland'
    ],
    'United Arab Emirates': [
      'Abu Dhabi', 'Dubai', 'Sharjah', 'Ajman', 'Umm Al Quwain', 'Ras Al Khaimah', 'Fujairah'
    ],
    'Australia': [
      'New South Wales', 'Victoria', 'Queensland', 'Western Australia',
      'South Australia', 'Tasmania', 'Australian Capital Territory', 'Northern Territory'
    ],
    'Canada': [
      'Ontario', 'Quebec', 'British Columbia', 'Alberta', 'Manitoba', 'Saskatchewan',
      'Nova Scotia', 'New Brunswick', 'Newfoundland and Labrador', 'Prince Edward Island',
      'Northwest Territories', 'Yukon', 'Nunavut'
    ],
    'Singapore': [
      'Central Region', 'East Region', 'North Region', 'North-East Region', 'West Region'
    ]
  };

  // Data State
  myHotels: any[] = [];
  selectedHotel: any = null;
  roomTypes: any[] = [];
  roomBlocks: any[] = [];
  managerReservations: any[] = [];
  filteredReservations: any[] = [];

  isLoading: boolean = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // ==================== REPORTING DATE-RANGE FILTER STATE ====================
  showDateRangeDropdown: boolean = false;
  activePreset: string = 'last7';
  startDate: string = '';
  endDate: string = '';
  prevStartDate: string = '';
  prevEndDate: string = '';
  durationDays: number = 7;
  selectedDateRangeLabel: string = 'Last 7 Days';
  prevDateRangeLabel: string = '';

  isCustomRangeActive: boolean = false;
  customStartDate: string = '';
  customEndDate: string = '';
  dateRangeValidationError: string | null = null;

  datePresets: DateRangePresetItem[] = [
    { id: 'today', label: 'Today' },
    { id: 'yesterday', label: 'Yesterday' },
    { id: 'last7', label: 'Last 7 Days' },
    { id: 'last30', label: 'Last 30 Days' },
    { id: 'thisMonth', label: 'This Month' },
    { id: 'lastMonth', label: 'Last Month' },
    { id: 'custom', label: 'Custom Range...' }
  ];

  // Header Dropdown States
  showNotifications: boolean = false;
  showUserDropdown: boolean = false;
  showPropertySelectorDropdown: boolean = false;
  notificationList: FormattedNotification[] = [];
  unreadNotificationsCount: number = 0;

  // ==================== DATE-AWARE KPI METRIC COMPARISONS ====================
  bookingsKpi: KpiComparison = this.defaultKpiComparison();
  nightsKpi: KpiComparison = this.defaultKpiComparison();
  occupancyKpi: KpiComparison = this.defaultKpiComparison();
  revenueKpi: KpiComparison = this.defaultKpiComparison();

  // Occupancy Overview Metrics (Date-Wise WP-03 Architecture)
  occupancyRate: number = 0;
  occupiedRoomNights: number = 0;
  heldRoomNights: number = 0;
  availableRoomNights: number = 0;
  totalPhysicalRoomNights: number = 0;
  blockedRoomNights: number = 0;
  applicableRoomNightCapacity: number = 0;
  donutOffset: number = 251.2;

  // Bookings Trend Chart (Dynamic for Reporting Period)
  bookingsTrendToggle: 'bookings' | 'revenue' = 'bookings';
  trendDays: TrendDayItem[] = [];
  maxChartValue: number = 40;
  maxRevenueChartValue: number = 50000;

  // Corrected Operational Alerts
  operationalAlerts = {
    arrivalsCount: 0,
    departuresCount: 0,
    activeRoomBlocksCount: 0,
    lowAvailabilityRoomsCount: 0
  };

  // Upcoming / Reporting Period Reservations
  upcomingReservations: any[] = [];

  // Modals State
  showAddHotelModal: boolean = false;
  showAddRoomModal: boolean = false;
  showBlockRoomModal: boolean = false;
  showPriceOverrideModal: boolean = false;
  showReservationDetailsModal: boolean = false;
  selectedReservation: any = null;

  // Form Models - Add Hotel
  hotelName: string = '';
  hotelCity: string = 'Bengaluru';
  hotelAddress: string = '';
  hotelStarRating: number = 4;
  hotelDescription: string = '';
  hotelAmenities: string = 'Swimming Pool, Free Wi-Fi, Gym, Spa, Multi-Cuisine Restaurant';
  hotelImageUrl: string = 'https://images.unsplash.com/photo-1566073771259-6a8506099945';

  // Form Models - Add Room
  selectedHotelForRoom: any = null;
  roomTypeName: string = 'Deluxe Room';
  roomMaxOccupancy: number = 2;
  roomPricePerNight: number = 4500;
  roomTotalCount: number = 20;
  roomAmenities: string = 'King Bed, Air Conditioning, City View, Free High-Speed Wi-Fi, Breakfast Included';

  // =========================================================================
  // WP-06: ADD ROOM TYPE INTEGRATED PAGE STATE
  // =========================================================================
  roomViewMode: 'list' | 'add' | 'edit' = 'list';

  // =========================================================================
  // WP-06: EDIT ROOM TYPE FULL-PAGE FORM STATE
  // =========================================================================
  editingRoomType: any = null;
  originalRoomType: any = null;
  isSubmittingEditRoomType: boolean = false;
  editRoomTypeFormError: string = '';
  editRoomTypeFormSuccess: string = '';
  editRoomTypeTouched: { roomTypeName?: boolean; maxOccupancy?: boolean; totalRooms?: boolean } = {};

  editCustomAmenityInput: string = '';
  editRoomCustomAmenityError: string = '';

  editRoomImageFile: File | null = null;
  editRoomImagePreview: string | null = null;
  editRoomImageError: string = '';
  isUploadingEditRoomImage: boolean = false;
  isEditRoomImageDragOver: boolean = false;
  isEditRoomImageRemoved: boolean = false;

  newRoomType: {
    roomTypeName: string;
    maxOccupancy: number;
    totalRooms: number | null;
    amenities: string[];
    imageUrl: string;
  } = {
    roomTypeName: '',
    maxOccupancy: 2,
    totalRooms: null,
    amenities: [],
    imageUrl: ''
  };

  predefinedRoomAmenities: { name: string; icon: string }[] = [
    { name: 'Wi-Fi', icon: 'fa-solid fa-wifi' },
    { name: 'AC', icon: 'fa-solid fa-snowflake' },
    { name: 'TV', icon: 'fa-solid fa-tv' },
    { name: 'Private Bathroom', icon: 'fa-solid fa-bath' },
    { name: 'Room Service', icon: 'fa-solid fa-bell-concierge' },
    { name: 'Heater', icon: 'fa-solid fa-temperature-arrow-up' },
    { name: 'Mini Fridge', icon: 'fa-solid fa-cube' },
    { name: 'Balcony', icon: 'fa-solid fa-door-open' },
    { name: 'Work Desk', icon: 'fa-solid fa-laptop' },
    { name: 'Wardrobe', icon: 'fa-solid fa-warehouse' }
  ];

  customAmenityInput: string = '';
  roomCustomAmenityError: string = '';

  roomImageFile: File | null = null;
  roomImagePreview: string | null = null;
  isUploadingRoomImage: boolean = false;
  isRoomImageDragOver: boolean = false;
  roomImageError: string = '';

  isSubmittingRoomType: boolean = false;
  roomTypeFormError: string = '';
  roomTypeFormSuccess: string = '';
  roomTypeTouched: { roomTypeName?: boolean; maxOccupancy?: boolean; totalRooms?: boolean } = {};

  // =========================================================================
  // WP-06: ROOM TYPES LISTING STATE (SEARCH, FILTER, SORT, VIEW MODE)
  // =========================================================================
  roomListingSearchQuery: string = '';
  roomListingStatusFilter: 'Active' | 'Archived' | 'All' = 'Active';
  roomListingSortBy: string = 'name-asc';
  roomListingViewMode: 'grid' | 'list' = 'grid';

  // Archive Room Modal State
  showArchiveRoomModal: boolean = false;
  roomToArchive: any = null;
  isArchivingRoom: boolean = false;
  archiveRoomError: string = '';
  archiveRoomSuccess: string = '';

  // Restore Room Modal State (WP-06)
  showRestoreRoomModal: boolean = false;
  roomToRestore: any = null;
  isRestoringRoom: boolean = false;
  restoreRoomError: string = '';
  restoreRoomSuccess: string = '';

  // Room Details Modal State (WP-06)
  showRoomDetailsModal: boolean = false;
  selectedRoomDetails: any = null;
  isLoadingRoomDetails: boolean = false;
  roomDetailsError: string = '';

  // Form Models - Block Rooms
  // ==================== INVENTORY & PRICING: BASE PRICE MANAGEMENT (WP-07.2) ====================
  inventorySubNav: 'inventory-calendar' | 'pricing-calendar' | 'base-price' | 'room-blocks' = 'base-price';
  basePriceSearchQuery: string = '';
  basePriceSortOption: string = 'name-asc';
  displayedBasePriceRooms: any[] = [];
  highestBasePrice: number = 0;
  lowestBasePrice: number = 0;

  // Edit Base Price Modal State
  showEditBasePriceModal: boolean = false;
  selectedRoomForPrice: any = null;
  activeEditPriceImage: string = '';
  editBasePriceValue: number | null = null;
  editBasePriceInputError: string = '';
  editBasePriceError: string = '';
  isUpdatingBasePrice: boolean = false;
  basePriceUpdateSuccess: string = '';

  // ==================== INVENTORY & PRICING: ROOM BLOCKS MANAGEMENT (WP-07.3) ====================
  Math = Math;
  roomBlocksList: any[] = [];
  displayedRoomBlocks: any[] = [];
  isLoadingRoomBlocks: boolean = false;
  roomBlocksSuccess: string = '';
  roomBlocksError: string = '';

  // Summary Metrics (Property-wide)
  totalBlocksCount: number = 0;
  activeBlocksCount: number = 0;
  releasedBlocksCount: number = 0;

  // Search & Filtering
  blockSearchQuery: string = '';
  selectedBlockRoomTypeFilter: string = 'all'; // 'all' or roomId string
  selectedBlockStatusFilter: string = 'all'; // 'all', 'ACTIVE', 'RELEASED'
  blockDateFilterStart: string = '';
  blockDateFilterEnd: string = '';
  showBlockDateFilterDropdown: boolean = false;

  // Pagination
  blocksCurrentPage: number = 1;
  blocksPageSize: number = 5;
  totalMatchingBlocks: number = 0;
  totalBlocksPages: number = 1;

  // Three-dot action menu
  activeBlockMenuId: number | null = null;

  // Modal 1: Create Room Block Modal State (Ref 2)
  showCreateBlockModal: boolean = false;
  activeRoomTypesForBlock: any[] = [];
  selectedRoomTypeForBlock: any = null;
  createBlockSelectedRoomId: number | null = null;
  createBlockMainImage: string = '';
  createBlockThumbnails: string[] = [];
  createBlockActiveThumbIndex: number = 0;
  createBlockCount: number = 1;
  createBlockStartDate: string = '';
  createBlockEndDate: string = '';
  createBlockReason: string = 'OPERATIONAL';
  createBlockNotes: string = '';
  createBlockError: string = '';
  isCreatingBlock: boolean = false;
  showCreateBlockDateDropdown: boolean = false;

  // Modal 2: Room Block Details Modal State (Ref 3)
  showBlockDetailsModal: boolean = false;
  selectedBlockForDetails: any = null;
  blockDetailsMainImage: string = '';
  blockDetailsThumbnails: string[] = [];
  blockDetailsActiveThumbIndex: number = 0;

  // Modal 3: Release Room Block Confirmation Modal State (Ref 4)
  showReleaseBlockModal: boolean = false;
  blockToRelease: any = null;
  isReleasingBlock: boolean = false;
  releaseBlockError: string = '';

  // Legacy compatibility fields
  blockRoomId: number | null = null;
  blockStartDate: string = '';
  blockEndDate: string = '';
  blockCount: number = 2;
  blockReason: string = 'MAINTENANCE';
  blockNotes: string = '';

  // Form Models - Price Override
  overrideRoomId: number | null = null;
  overrideStartDate: string = '';
  overrideEndDate: string = '';
  overridePrice: number = 5500;

  // Subscriptions
  private subscriptions: Subscription = new Subscription();

  constructor(
    private authService: AuthService,
    private hotelService: HotelService,
    private bookingService: BookingService,
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    this.initNotifications();
    this.initDefaultReportingPeriod();
    this.updateAvailableStates();
    this.loadInitialData();
  }

  ngOnDestroy(): void {
    document.body.style.overflow = '';
    this.subscriptions.unsubscribe();
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEscapeKeyDown(event: KeyboardEvent): void {
    if (this.showRoomDetailsModal) {
      this.closeRoomDetailsModal();
    } else if (this.showArchiveRoomModal) {
      this.cancelArchiveRoom();
    } else if (this.showRestoreRoomModal) {
      this.cancelRestoreRoom();
    } else if (this.showEditBasePriceModal) {
      this.closeEditBasePriceModal();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.date-range-dropdown-container')) {
      this.showDateRangeDropdown = false;
    }
    if (!target.closest('.notifications-dropdown-container')) {
      this.showNotifications = false;
    }
    if (!target.closest('.user-dropdown-container')) {
      this.showUserDropdown = false;
    }
    if (!target.closest('.property-dropdown-container')) {
      this.showPropertySelectorDropdown = false;
    }
  }

  // ==================== REPORTING DATE RANGE INITIALIZATION & PRESETS ====================
  private initDefaultReportingPeriod(): void {
    this.selectPreset('last7', false);
  }

  selectPreset(presetId: string, triggerRefresh: boolean = true): void {
    const today = new Date();
    const todayIso = this.formatDateIso(today);
    let start = todayIso;
    let end = todayIso;
    let label = 'Today';

    switch (presetId) {
      case 'today': {
        start = todayIso;
        end = todayIso;
        label = `Today (${this.formatDateShort(start)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'yesterday': {
        const y = new Date();
        y.setDate(today.getDate() - 1);
        start = this.formatDateIso(y);
        end = start;
        label = `Yesterday (${this.formatDateShort(start)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'last7': {
        const d = new Date();
        d.setDate(today.getDate() - 6);
        start = this.formatDateIso(d);
        end = todayIso;
        label = `Last 7 Days (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'last30': {
        const d = new Date();
        d.setDate(today.getDate() - 29);
        start = this.formatDateIso(d);
        end = todayIso;
        label = `Last 30 Days (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'thisMonth': {
        const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
        const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
        start = this.formatDateIso(firstDay);
        end = this.formatDateIso(lastDay);
        label = `This Month (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'lastMonth': {
        const firstDayLastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        const lastDayLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);
        start = this.formatDateIso(firstDayLastMonth);
        end = this.formatDateIso(lastDayLastMonth);
        label = `Last Month (${this.formatDateShort(start)} – ${this.formatDateShort(end)})`;
        this.isCustomRangeActive = false;
        break;
      }
      case 'custom': {
        this.isCustomRangeActive = true;
        this.customStartDate = this.startDate;
        this.customEndDate = this.endDate;
        return;
      }
    }

    this.activePreset = presetId;
    this.setReportingRange(start, end, label);

    if (triggerRefresh) {
      this.showDateRangeDropdown = false;
      this.loadDashboardData();
    }
  }

  applyCustomDateRange(): void {
    if (!this.customStartDate || !this.customEndDate) {
      this.dateRangeValidationError = 'Please select both start and end dates.';
      return;
    }
    if (this.customStartDate > this.customEndDate) {
      this.dateRangeValidationError = 'Start date cannot exceed end date.';
      return;
    }

    this.dateRangeValidationError = null;
    this.activePreset = 'custom';
    const label = `Custom (${this.formatDateShort(this.customStartDate)} – ${this.formatDateShort(this.customEndDate)})`;
    this.setReportingRange(this.customStartDate, this.customEndDate, label);
    this.showDateRangeDropdown = false;
    this.loadDashboardData();
  }

  private setReportingRange(start: string, end: string, label: string): void {
    this.startDate = start;
    this.endDate = end;
    this.selectedDateRangeLabel = label;

    // Previous equivalent period calculation
    const prev = this.calculateEquivalentPreviousPeriod(start, end);
    this.prevStartDate = prev.prevStartDate;
    this.prevEndDate = prev.prevEndDate;
    this.durationDays = prev.durationDays;
    if (prev.prevStartDate === prev.prevEndDate) {
      this.prevDateRangeLabel = `${this.formatDateShort(prev.prevStartDate)} (1d)`;
    } else {
      this.prevDateRangeLabel = `${this.formatDateShort(prev.prevStartDate)} – ${this.formatDateShort(prev.prevEndDate)} (${prev.durationDays}d)`;
    }
  }

  calculateEquivalentPreviousPeriod(start: string, end: string): { prevStartDate: string; prevEndDate: string; durationDays: number } {
    const dStart = new Date(start + 'T00:00:00');
    const dEnd = new Date(end + 'T00:00:00');
    const diffTime = dEnd.getTime() - dStart.getTime();
    const durationDays = Math.round(diffTime / (1000 * 60 * 60 * 24)) + 1;

    // Previous period ends immediately before current start date
    const pEnd = new Date(dStart.getTime() - (24 * 60 * 60 * 1000));
    const pStart = new Date(pEnd.getTime() - ((durationDays - 1) * 24 * 60 * 60 * 1000));

    return {
      prevStartDate: this.formatDateIso(pStart),
      prevEndDate: this.formatDateIso(pEnd),
      durationDays
    };
  }

  toggleDateRangeDropdown(event: Event): void {
    event.stopPropagation();
    this.showDateRangeDropdown = !this.showDateRangeDropdown;
    this.showNotifications = false;
    this.showUserDropdown = false;
    this.showPropertySelectorDropdown = false;
  }

  // ==================== DATA LOADING (SHARED FILTER STATE) ====================
  loadInitialData(): void {
    if (!this.currentUser) return;
    this.isLoading = true;
    this.errorMessage = null;

    this.loadManagerDrafts();
    this.hotelService.getHotelsByManager(this.currentUser.id).subscribe({
      next: (hotels) => {
        this.myHotels = hotels || [];
        if (this.myHotels.length > 0) {
          if (!this.selectedHotel || !this.myHotels.some(h => h.id === this.selectedHotel.id)) {
            this.selectedHotel = this.myHotels[0];
          }
        } else {
          this.selectedHotel = null;
        }
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Failed to load manager hotels:', err);
        this.errorMessage = 'Failed to load hotel properties. Please refresh or try again.';
        this.isLoading = false;
      }
    });
  }

  selectHotel(hotel: any): void {
    this.selectedHotel = hotel;
    this.showPropertySelectorDropdown = false;
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    if (!this.currentUser) return;
    this.isLoading = true;

    if (!this.selectedHotel) {
      this.roomTypes = [];
      this.roomBlocks = [];
      this.refreshBasePriceListing();
        this.refreshRoomBlocksListing();
      this.fetchReservationsAndCompute(null, null);
      return;
    }

    const hotelId = this.selectedHotel.id;

    // Load Room Types & Room Blocks in parallel with inventory summary
    forkJoin({
      roomTypes: this.hotelService.getRoomTypes(hotelId).pipe(catchError(() => of([]))),
      roomBlocks: this.hotelService.getRoomBlocks(hotelId).pipe(catchError(() => of([]))),
      inventorySummary: this.hotelService.getHotelInventorySummary(hotelId, this.startDate, this.endDate).pipe(catchError(() => of(null))),
      prevInventorySummary: this.hotelService.getHotelInventorySummary(hotelId, this.prevStartDate, this.prevEndDate).pipe(catchError(() => of(null)))
    }).subscribe({
      next: (res) => {
        this.roomTypes = res.roomTypes || [];
        this.roomBlocks = res.roomBlocks || [];
        this.refreshBasePriceListing();
        this.fetchReservationsAndCompute(res.inventorySummary, res.prevInventorySummary);
      },
      error: (err) => {
        console.warn('Dashboard data fetch error:', err);
        this.fetchReservationsAndCompute(null, null);
      }
    });
  }

  private fetchReservationsAndCompute(currInventorySummary: any, prevInventorySummary: any): void {
    this.bookingService.getProviderBookings(this.currentUser!.id).subscribe({
      next: (reservations) => {
        this.managerReservations = reservations || [];
        this.filterAndFetchRevenue(currInventorySummary, prevInventorySummary);
      },
      error: (err) => {
        console.error('Failed to load reservations:', err);
        this.managerReservations = [];
        this.filterAndFetchRevenue(currInventorySummary, prevInventorySummary);
      }
    });
  }

  private filterAndFetchRevenue(currInventorySummary: any, prevInventorySummary: any): void {
    if (this.selectedHotel) {
      this.filteredReservations = this.managerReservations.filter(res =>
        res.serviceId === this.selectedHotel.id ||
        (res.serviceTitle && res.serviceTitle.toLowerCase().includes(this.selectedHotel.name?.toLowerCase()))
      );
    } else {
      this.filteredReservations = [...this.managerReservations];
    }

    const bookingRefs = this.filteredReservations.map(r => r.bookingRef).filter(ref => !!ref);

    forkJoin({
      currRevenue: this.bookingService.getHotelRevenueSummary({
        hotelId: this.selectedHotel ? this.selectedHotel.id : undefined,
        bookingRefs,
        startDate: this.startDate,
        endDate: this.endDate
      }).pipe(catchError((err) => {
        console.warn('Current period revenue summary fetch failed:', err);
        return of(null);
      })),
      prevRevenue: this.bookingService.getHotelRevenueSummary({
        hotelId: this.selectedHotel ? this.selectedHotel.id : undefined,
        bookingRefs,
        startDate: this.prevStartDate,
        endDate: this.prevEndDate
      }).pipe(catchError((err) => {
        console.warn('Previous period revenue summary fetch failed:', err);
        return of(null);
      }))
    }).subscribe({
      next: (revRes) => {
        this.computeDashboardMetrics(currInventorySummary, prevInventorySummary, revRes.currRevenue, revRes.prevRevenue);
        this.isLoading = false;
      },
      error: () => {
        this.computeDashboardMetrics(currInventorySummary, prevInventorySummary, null, null);
        this.isLoading = false;
      }
    });
  }

  // ==================== ACCURATE DATE-AWARE METRIC CALCULATIONS ====================
  private computeDashboardMetrics(
    currInv: any,
    prevInv: any,
    currRevenueSummary: HotelRevenueReportResponse | null,
    prevRevenueSummary: HotelRevenueReportResponse | null
  ): void {
    // 0. Filter reservations for selected hotel (already filtered in filterAndFetchRevenue)
    // ================= 1. TOTAL BOOKINGS (CREATED IN PERIOD) =================
    const currBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, this.startDate, this.endDate);
    const prevBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, this.prevStartDate, this.prevEndDate);

    const currBookingsCount = currBookings.length;
    const prevBookingsCount = prevBookings.length;
    this.bookingsKpi = this.buildKpiComparison(currBookingsCount, prevBookingsCount);

    // ================= 2. ROOM NIGHTS (STAY OVERLAP IN PERIOD) =================
    const currRoomNights = this.calculatePeriodRoomNights(this.filteredReservations, this.startDate, this.endDate);
    const prevRoomNights = this.calculatePeriodRoomNights(this.filteredReservations, this.prevStartDate, this.prevEndDate);
    this.nightsKpi = this.buildKpiComparison(currRoomNights, prevRoomNights);

    // ================= 3. OCCUPANCY RATE (WP-03 DATE-WISE ARCHITECTURE) =================
    if (currInv) {
      this.totalPhysicalRoomNights = currInv.totalPhysicalRoomNights || 0;
      this.occupiedRoomNights = currInv.occupiedRoomNights || 0;
      this.heldRoomNights = currInv.heldRoomNights || 0;
      this.blockedRoomNights = currInv.blockedRoomNights || 0;
      this.availableRoomNights = currInv.availableRoomNights || 0;
      this.applicableRoomNightCapacity = currInv.applicableRoomNightCapacity || 0;
      this.occupancyRate = currInv.occupancyRate || 0;
    } else {
      // Fallback: derive date-wise from roomTypes and eligible room nights
      const totalRooms = this.roomTypes.reduce((sum, r) => sum + (Number(r.totalRooms) || 0), 0);
      this.totalPhysicalRoomNights = totalRooms * this.durationDays;
      this.occupiedRoomNights = currRoomNights;
      this.heldRoomNights = 0;
      this.blockedRoomNights = 0;
      this.applicableRoomNightCapacity = this.totalPhysicalRoomNights;
      this.availableRoomNights = Math.max(0, this.applicableRoomNightCapacity - this.occupiedRoomNights - this.heldRoomNights);
      this.occupancyRate = this.applicableRoomNightCapacity > 0
        ? Math.round(((this.occupiedRoomNights / this.applicableRoomNightCapacity) * 100) * 10) / 10
        : 0;
    }

    const prevOccupancyRate = prevInv ? (prevInv.occupancyRate || 0) : 0;
    this.occupancyKpi = this.buildKpiComparison(this.occupancyRate, prevOccupancyRate, true);
    this.donutOffset = 251.2 * (1 - (Math.min(100, Math.max(0, this.occupancyRate)) / 100));

    // ================= 4. NET REVENUE (ACTUAL TRANSACTION-BASED REVENUE) =================
    let currNetRevenue = 0;
    let prevNetRevenue = 0;

    if (currRevenueSummary) {
      currNetRevenue = currRevenueSummary.netRevenue;
    } else {
      const currRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, this.startDate, this.endDate);
      currNetRevenue = currRevenueData.netRevenue;
    }

    if (prevRevenueSummary) {
      prevNetRevenue = prevRevenueSummary.netRevenue;
    } else {
      const prevRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, this.prevStartDate, this.prevEndDate);
      prevNetRevenue = prevRevenueData.netRevenue;
    }

    this.revenueKpi = this.buildKpiComparison(currNetRevenue, prevNetRevenue);

    // ================= 5. BOOKING TRENDS CHART =================
    this.buildTrendsChartData(currInv, currRevenueSummary);

    // ================= 6. UPCOMING / REPORTING PERIOD RESERVATIONS =================
    // Filter strictly by check-in date within the reporting period; exclude CANCELLED, FAILED, EXPIRED.
    this.upcomingReservations = this.filteredReservations
      .filter(r => {
        if (r.status === 'CANCELLED' || r.status === 'FAILED' || r.status === 'EXPIRED') {
          return false;
        }
        const checkIn = r.checkInDate || this.extractDateFromBooking(r, 'in');
        return checkIn && checkIn >= this.startDate && checkIn <= this.endDate;
      })
      .sort((a, b) => {
        const dateA = a.checkInDate || this.extractDateFromBooking(a, 'in') || '';
        const dateB = b.checkInDate || this.extractDateFromBooking(b, 'in') || '';
        return dateA.localeCompare(dateB);
      })
      .slice(0, 5);

    // ================= 7. CORRECTED OPERATIONAL ALERTS =================
    this.computeOperationalAlerts(currInv);
  }

  // ==================== CALCULATION HELPERS ====================
  private filterBookingsCreatedInPeriod(bookings: any[], start: string, end: string): any[] {
    return bookings.filter(b => {
      // Exclude cancelled, failed, expired, unconfirmed
      if (b.status === 'CANCELLED' || b.status === 'FAILED' || b.status === 'EXPIRED') {
        return false;
      }
      const creationDate = b.bookingDate ? b.bookingDate.split('T')[0] : (b.confirmedAt ? b.confirmedAt.split('T')[0] : '');
      return creationDate >= start && creationDate <= end;
    });
  }

  private calculatePeriodRoomNights(bookings: any[], start: string, end: string): number {
    let nightsCount = 0;
    const startDate = new Date(start + 'T00:00:00');
    const endDate = new Date(end + 'T00:00:00');

    bookings.forEach(b => {
      // Exclude unpaid holds or cancelled bookings
      if (b.status !== 'CONFIRMED' && b.status !== 'CHECKED_IN' && b.status !== 'CHECKED_OUT' && b.status !== 'NO_SHOW') {
        return;
      }

      const checkInStr = b.checkInDate || this.extractDateFromBooking(b, 'in');
      const checkOutStr = b.checkOutDate || this.extractDateFromBooking(b, 'out');
      if (!checkInStr || !checkOutStr) return;

      const bCheckIn = new Date(checkInStr + 'T00:00:00');
      const bCheckOut = new Date(checkOutStr + 'T00:00:00');

      let cur = new Date(bCheckIn);
      let overlap = 0;
      // Check-in inclusive, checkout exclusive: [checkIn, checkOut)
      while (cur < bCheckOut) {
        if (cur >= startDate && cur <= endDate) {
          overlap++;
        }
        cur.setDate(cur.getDate() + 1);
      }

      const roomCount = this.extractRoomCountFromBooking(b);
      nightsCount += (overlap * roomCount);
    });

    return nightsCount;
  }

  private calculatePeriodNetRevenue(bookings: any[], start: string, end: string): { grossRevenue: number; refunds: number; netRevenue: number } {
    let grossRevenue = 0;
    let refunds = 0;

    bookings.forEach(b => {
      // 1. Eligible successful payment captured
      const hasSuccessfulPayment = b.status === 'CONFIRMED' ||
                                   b.status === 'CHECKED_IN' ||
                                   b.status === 'CHECKED_OUT' ||
                                   b.status === 'NO_SHOW' ||
                                   (b.status === 'CANCELLED' && b.confirmedAt);

      if (hasSuccessfulPayment) {
        const paymentDate = b.confirmedAt ? b.confirmedAt.split('T')[0] : (b.bookingDate ? b.bookingDate.split('T')[0] : '');
        if (paymentDate && paymentDate >= start && paymentDate <= end) {
          grossRevenue += (Number(b.totalAmount) || 0);
        }
      }

      // 2. Applicable refunds processed
      if (b.status === 'CANCELLED' && (b.refundStatus === 'PROCESSED' || (Number(b.refundAmount) || 0) > 0)) {
        const refundDate = b.cancelledAt ? b.cancelledAt.split('T')[0] : (b.bookingDate ? b.bookingDate.split('T')[0] : '');
        if (refundDate && refundDate >= start && refundDate <= end) {
          refunds += (Number(b.refundAmount) || 0);
        }
      }
    });

    const netRevenue = Math.max(0, grossRevenue - refunds);
    return { grossRevenue, refunds, netRevenue };
  }

  private computeOperationalAlerts(currInv: any): void {
    let arrivals = 0;
    let departures = 0;

    // 1. Arrivals: CONFIRMED reservations with checkInDate in reporting range (exclude PENDING_PAYMENT)
    this.filteredReservations.forEach(b => {
      const checkInStr = b.checkInDate || this.extractDateFromBooking(b, 'in');
      if (checkInStr && checkInStr >= this.startDate && checkInStr <= this.endDate) {
        if (b.status === 'CONFIRMED') {
          arrivals++;
        }
      }
    });

    // 2. Departures: CHECKED_IN guests with checkOutDate in reporting range (exclude already CHECKED_OUT)
    this.filteredReservations.forEach(b => {
      const checkOutStr = b.checkOutDate || this.extractDateFromBooking(b, 'out');
      if (checkOutStr && checkOutStr >= this.startDate && checkOutStr <= this.endDate) {
        if (b.status === 'CHECKED_IN') {
          departures++;
        }
      }
    });

    // 3. Active Room Blocks: Overlap rule [startDate, endDate)
    // block.startDate <= this.endDate && block.endDate > this.startDate
    const activeBlocks = this.roomBlocks.filter(b =>
      b.status === 'ACTIVE' &&
      b.startDate <= this.endDate &&
      b.endDate > this.startDate
    ).length;

    // 4. Low Availability: Unique room types with date-wise availableRooms <= 2 on any date in period
    let lowAvailCount = 0;
    if (currInv && currInv.dailySummaries) {
      // Check if daily available drops <= 2
      const hasLow = currInv.dailySummaries.some((d: any) => d.availableRooms !== undefined && d.availableRooms <= 2);
      lowAvailCount = hasLow ? Math.min(this.roomTypes.length, 2) : 0;
    } else {
      lowAvailCount = this.roomTypes.filter(rt =>
        rt.availableRooms !== undefined && rt.availableRooms !== null && rt.availableRooms <= 2
      ).length;
    }

    this.operationalAlerts = {
      arrivalsCount: arrivals,
      departuresCount: departures,
      activeRoomBlocksCount: activeBlocks,
      lowAvailabilityRoomsCount: lowAvailCount
    };
  }

  private buildTrendsChartData(currInv: any, currRev: HotelRevenueReportResponse | null = null): void {
    const rawItems: { isoStr: string; dateLabel: string; count: number; revenue: number }[] = [];
    const dStart = new Date(this.startDate + 'T00:00:00');
    const dEnd = new Date(this.endDate + 'T00:00:00');

    let maxCount = 0;
    let maxRev = 0;

    let cur = new Date(dStart);

    while (cur <= dEnd) {
      const isoStr = this.formatDateIso(cur);
      const dateLabel = this.formatDateShort(isoStr);

      // Bookings created on this day
      const dayBookings = this.filterBookingsCreatedInPeriod(this.filteredReservations, isoStr, isoStr);
      const dayCount = dayBookings.length;

      // Net revenue collected on this day from actual payment transactions
      let dayRev = 0;
      if (currRev && currRev.dailyBreakdown) {
        const dayItem = currRev.dailyBreakdown.find(d => d.date === isoStr);
        dayRev = dayItem ? dayItem.netRevenue : 0;
      } else {
        const dayRevenueData = this.calculatePeriodNetRevenue(this.filteredReservations, isoStr, isoStr);
        dayRev = dayRevenueData.netRevenue;
      }

      if (dayCount > maxCount) maxCount = dayCount;
      if (dayRev > maxRev) maxRev = dayRev;

      rawItems.push({ isoStr, dateLabel, count: dayCount, revenue: dayRev });
      cur.setDate(cur.getDate() + 1);
    }

    const totalDays = rawItems.length;

    // Adaptive step calculation based on total number of days:
    // Short ranges (<= 8 days): display all dates (step = 1)
    // Medium-short (9 - 16 days): every 2nd day (step = 2)
    // Medium (17 - 24 days): every 3rd day (step = 3)
    // Long (25 - 35 days, e.g. 30 days / 1 month): every 5th day (step = 5)
    // Very long (> 35 days): target 5 to 7 total labels
    let step = 1;
    if (totalDays <= 8) {
      step = 1;
    } else if (totalDays <= 16) {
      step = 2;
    } else if (totalDays <= 24) {
      step = 3;
    } else if (totalDays <= 35) {
      step = 5;
    } else {
      step = Math.ceil(totalDays / 6);
    }

    // Determine which days display an X-axis date label
    const shouldShow = new Array(totalDays).fill(false);
    if (totalDays <= 8) {
      shouldShow.fill(true);
    } else {
      let lastShown = -999;
      for (let i = 0; i < totalDays; i++) {
        const isStepMilestone = (i % step === 0);
        const isLast = (i === totalDays - 1);

        if (isStepMilestone) {
          // If milestone is too close to the final day, skip it so the final day displays without colliding
          if (totalDays - 1 - i >= Math.max(2, Math.floor(step * 0.7))) {
            shouldShow[i] = true;
            lastShown = i;
          }
        }

        if (isLast) {
          // Always show the last day if it has at least 2 days clearance from the previous milestone
          if (i - lastShown >= 2) {
            shouldShow[i] = true;
          }
        }
      }
    }

    this.maxChartValue = Math.max(40, Math.ceil((maxCount + 5) / 10) * 10);
    this.maxRevenueChartValue = Math.max(50000, Math.ceil((maxRev + 5000) / 10000) * 10000);

    const days: TrendDayItem[] = rawItems.map((item, idx) => ({
      dateLabel: item.dateLabel,
      fullDate: item.isoStr,
      count: item.count,
      revenue: item.revenue,
      heightPercent: item.count > 0
        ? Math.min(100, Math.max(2, Math.round((item.count / this.maxChartValue) * 100)))
        : 0,
      revenueHeightPercent: item.revenue > 0
        ? Math.min(100, Math.max(2, Math.round((item.revenue / this.maxRevenueChartValue) * 100)))
        : 0,
      heightPx: Math.max(4, Math.round((item.count / this.maxChartValue) * 200)),
      revenueHeightPx: item.revenue > 0
        ? Math.max(4, Math.round((item.revenue / this.maxRevenueChartValue) * 200))
        : 4,
      showLabel: shouldShow[idx]
    }));

    this.trendDays = days;
  }

  toggleBookingsTrend(mode: 'bookings' | 'revenue'): void {
    this.bookingsTrendToggle = mode;
  }

  buildKpiComparison(curr: number, prev: number, isPercentPoints: boolean = false, hasHistoricalData: boolean = true): KpiComparison {
    const diff = Math.round((curr - prev) * 10) / 10;
    if (!hasHistoricalData) {
      return {
        currentValue: curr,
        previousValue: prev,
        diff: 0,
        percentageChange: null,
        isPositive: false,
        isNegative: false,
        isNeutral: true,
        trendText: 'No prior data',
        hasData: false
      };
    }

    if (isPercentPoints) {
      const isPos = diff > 0;
      const isNeg = diff < 0;
      const isNeut = diff === 0;
      const sign = diff > 0 ? '+' : '';
      return {
        currentValue: curr,
        previousValue: prev,
        diff,
        percentageChange: diff,
        isPositive: isPos,
        isNegative: isNeg,
        isNeutral: isNeut,
        trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${diff} pp`,
        hasData: true
      };
    }

    if (prev === 0) {
      if (curr === 0) {
        return {
          currentValue: curr,
          previousValue: 0,
          diff: 0,
          percentageChange: 0,
          isPositive: false,
          isNegative: false,
          isNeutral: true,
          trendText: '0%',
          hasData: true
        };
      } else {
        return {
          currentValue: curr,
          previousValue: 0,
          diff: curr,
          percentageChange: 100,
          isPositive: true,
          isNegative: false,
          isNeutral: false,
          trendText: `↑ +${curr} (new)`,
          hasData: true
        };
      }
    }

    const pct = Math.round(((curr - prev) / prev) * 100);
    const isPos = pct > 0;
    const isNeg = pct < 0;
    const isNeut = pct === 0;
    const sign = pct > 0 ? '+' : '';
    return {
      currentValue: curr,
      previousValue: prev,
      diff,
      percentageChange: pct,
      isPositive: isPos,
      isNegative: isNeg,
      isNeutral: isNeut,
      trendText: `${isPos ? '↑ ' : isNeg ? '↓ ' : ''}${sign}${pct}%`,
      hasData: true
    };
  }

  private defaultKpiComparison(): KpiComparison {
    return {
      currentValue: 0,
      previousValue: 0,
      diff: 0,
      percentageChange: 0,
      isPositive: false,
      isNegative: false,
      isNeutral: true,
      trendText: '0%',
      hasData: false
    };
  }

  // ==================== NOTIFICATIONS ====================
  private initNotifications(): void {
    this.subscriptions.add(
      this.notificationService.notifications$.subscribe(notifications => {
        this.notificationList = notifications;
      })
    );
    this.subscriptions.add(
      this.notificationService.unreadCount$.subscribe(count => {
        this.unreadNotificationsCount = count;
      })
    );
  }

  toggleNotifications(event: Event): void {
    event.stopPropagation();
    this.showNotifications = !this.showNotifications;
    this.showUserDropdown = false;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  markNotificationsRead(): void {
    this.notificationService.markAllAsRead().subscribe();
  }

  handleNotificationClick(n: FormattedNotification): void {
    if (!n.isRead) {
      this.notificationService.markAsRead(n.id).subscribe();
    }
    this.showNotifications = false;
  }

  // ==================== USER & PROFILE ====================
  toggleUserDropdown(event: Event): void {
    event.stopPropagation();
    this.showUserDropdown = !this.showUserDropdown;
    this.showNotifications = false;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  togglePropertyDropdown(event: Event): void {
    event.stopPropagation();
    this.showPropertySelectorDropdown = !this.showPropertySelectorDropdown;
    this.showNotifications = false;
    this.showUserDropdown = false;
    this.showDateRangeDropdown = false;
  }

  toggleSidebar(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleMobileSidebar(): void {
    this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
  }

  getInitials(): string {
    if (!this.currentUser?.fullName) return 'HM';
    const parts = this.currentUser.fullName.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ==================== QUICK ACTIONS & MODALS ====================
  openAddHotelModal(): void {
    this.showAddHotelModal = true;
    this.showPropertySelectorDropdown = false;
    this.showDateRangeDropdown = false;
  }

  submitAddHotel(): void {
    if (!this.currentUser) return;
    if (!this.hotelName.trim()) {
      alert('Please enter a hotel property name.');
      return;
    }

    const payload = {
      managerId: this.currentUser.id,
      managerName: this.currentUser.fullName,
      name: this.hotelName.trim(),
      city: this.hotelCity.trim(),
      address: this.hotelAddress.trim(),
      starRating: this.hotelStarRating,
      description: this.hotelDescription.trim(),
      amenities: this.hotelAmenities.trim(),
      imageUrl: this.hotelImageUrl.trim()
    };

    this.hotelService.addHotel(payload).subscribe({
      next: () => {
        alert('Hotel Property registered successfully!');
        this.showAddHotelModal = false;
        this.hotelName = '';
        this.hotelAddress = '';
        this.hotelDescription = '';
        this.loadInitialData();
      },
      error: (err) => {
        console.error('Add hotel error:', err);
        alert(err.error?.message || 'Failed to register hotel property.');
      }
    });
  }

  getHotelInitials(name?: string): string {
    if (!name) return this.getInitials();
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0].substring(0, 2).toUpperCase();
  }

  startAddRoomType(hotel?: any): void {
    this.selectedHotelForRoom = hotel || this.selectedHotel;
    if (!this.selectedHotelForRoom && this.myHotels.length > 0) {
      this.selectedHotelForRoom = this.myHotels[0];
    }
    if (!this.selectedHotelForRoom) {
      alert('Please register or select a hotel property before adding room types.');
      return;
    }
    this.newRoomType = {
      roomTypeName: '',
      maxOccupancy: 2,
      totalRooms: null,
      amenities: [],
      imageUrl: ''
    };
    this.customAmenityInput = '';
    this.roomCustomAmenityError = '';
    this.roomImageFile = null;
    this.roomImagePreview = null;
    this.roomImageError = '';
    this.isUploadingRoomImage = false;
    this.isRoomImageDragOver = false;
    this.isSubmittingRoomType = false;
    this.roomTypeFormError = '';
    this.roomTypeFormSuccess = '';
    this.roomTypeTouched = {};
    this.roomViewMode = 'add';
  }

  cancelAddRoomType(): void {
    this.roomViewMode = 'list';
    this.roomTypeFormError = '';
    this.roomTypeFormSuccess = '';
  }

  incrementOccupancy(): void {
    if (this.newRoomType.maxOccupancy < 20) {
      this.newRoomType.maxOccupancy++;
    }
    this.roomTypeTouched.maxOccupancy = true;
  }

  decrementOccupancy(): void {
    if (this.newRoomType.maxOccupancy > 1) {
      this.newRoomType.maxOccupancy--;
    }
    this.roomTypeTouched.maxOccupancy = true;
  }

  onOccupancyChange(): void {
    this.roomTypeTouched.maxOccupancy = true;
    if (this.newRoomType.maxOccupancy != null) {
      if (this.newRoomType.maxOccupancy < 1) this.newRoomType.maxOccupancy = 1;
      if (this.newRoomType.maxOccupancy > 20) this.newRoomType.maxOccupancy = 20;
    }
  }

  incrementTotalRooms(): void {
    if (this.newRoomType.totalRooms == null || this.newRoomType.totalRooms < 1) {
      this.newRoomType.totalRooms = 1;
    } else {
      this.newRoomType.totalRooms++;
    }
    this.roomTypeTouched.totalRooms = true;
  }

  decrementTotalRooms(): void {
    if (this.newRoomType.totalRooms != null && this.newRoomType.totalRooms > 1) {
      this.newRoomType.totalRooms--;
    }
    this.roomTypeTouched.totalRooms = true;
  }

  isAmenitySelected(name: string): boolean {
    return this.newRoomType.amenities.some(a => a.toLowerCase() === name.toLowerCase());
  }

  toggleAmenity(name: string): void {
    const idx = this.newRoomType.amenities.findIndex(a => a.toLowerCase() === name.toLowerCase());
    if (idx >= 0) {
      this.newRoomType.amenities.splice(idx, 1);
    } else {
      this.newRoomType.amenities.push(name);
    }
  }

  getRoomAmenityIcon(name: string): string {
    const match = this.predefinedRoomAmenities.find(a => a.name.toLowerCase() === name.toLowerCase());
    if (match) return match.icon;
    const lower = name.toLowerCase();
    if (lower.includes('bath') || lower.includes('tub')) return 'fa-solid fa-bath';
    if (lower.includes('bed')) return 'fa-solid fa-bed';
    if (lower.includes('coffee') || lower.includes('tea')) return 'fa-solid fa-mug-hot';
    if (lower.includes('safe') || lower.includes('locker')) return 'fa-solid fa-vault';
    if (lower.includes('view')) return 'fa-solid fa-mountain-sun';
    if (lower.includes('iron')) return 'fa-solid fa-shirt';
    return 'fa-solid fa-check';
  }

  addCustomAmenity(): void {
    this.roomCustomAmenityError = '';
    const trimmed = (this.customAmenityInput || '').trim();
    if (!trimmed) return;

    if (this.newRoomType.amenities.some(a => a.toLowerCase() === trimmed.toLowerCase())) {
      this.roomCustomAmenityError = `"${trimmed}" is already in the selected amenities.`;
      return;
    }

    const predefined = this.predefinedRoomAmenities.find(a => a.name.toLowerCase() === trimmed.toLowerCase());
    if (predefined) {
      this.newRoomType.amenities.push(predefined.name);
    } else {
      this.newRoomType.amenities.push(trimmed);
    }
    this.customAmenityInput = '';
  }

  removeAmenity(name: string): void {
    this.newRoomType.amenities = this.newRoomType.amenities.filter(a => a.toLowerCase() !== name.toLowerCase());
  }

  onRoomImageDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isRoomImageDragOver = true;
  }

  onRoomImageDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isRoomImageDragOver = false;
  }

  onRoomImageDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isRoomImageDragOver = false;
    if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length > 0) {
      this.processRoomImageFile(event.dataTransfer.files[0]);
    }
  }

  onRoomImageSelected(event: any): void {
    if (event.target && event.target.files && event.target.files.length > 0) {
      this.processRoomImageFile(event.target.files[0]);
    }
  }

  private processRoomImageFile(file: File): void {
    this.roomImageError = '';
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type.toLowerCase()) && !file.name.match(/\.(jpg|jpeg|png|webp)$/i)) {
      this.roomImageError = 'Supported formats are JPG, JPEG, PNG, and WebP.';
      return;
    }

    const maxSize = 1 * 1024 * 1024; // 1 MB
    if (file.size > maxSize) {
      const sizeKb = Math.round(file.size / 1024);
      this.roomImageError = `Image size (${sizeKb} KB) exceeds the 1 MB limit. Please select a smaller image.`;
      return;
    }

    this.roomImageFile = file;
    this.isUploadingRoomImage = true;

    this.hotelService.uploadHotelImage(file, 'room').subscribe({
      next: (res) => {
        this.isUploadingRoomImage = false;
        this.newRoomType.imageUrl = res.imageUrl;
        this.roomImagePreview = res.imageUrl;
      },
      error: (err) => {
        this.isUploadingRoomImage = false;
        console.error('Room image upload failed:', err);
        this.roomImageError = err.error?.message || 'Failed to upload image. Please try again.';
      }
    });
  }

  removeRoomImage(): void {
    this.roomImageFile = null;
    this.roomImagePreview = null;
    this.newRoomType.imageUrl = '';
    this.roomImageError = '';
  }

  isDuplicateRoomTypeName(name: string): boolean {
    if (!name || !name.trim()) return false;
    const trimmed = name.trim().toLowerCase();
    return this.roomTypes.some(rt => rt.status !== 'ARCHIVED' && (rt.roomTypeName || '').trim().toLowerCase() === trimmed);
  }

  isRoomTypeNameValid(): boolean {
    const name = (this.newRoomType.roomTypeName || '').trim();
    if (!name || name.length > 100) return false;
    if (this.isDuplicateRoomTypeName(name)) return false;
    return true;
  }

  isMaxOccupancyValid(): boolean {
    const occ = this.newRoomType.maxOccupancy;
    return occ != null && occ >= 1 && occ <= 20;
  }

  isTotalRoomsValid(): boolean {
    const rooms = this.newRoomType.totalRooms;
    return rooms != null && Number(rooms) >= 1;
  }

  isRoomTypeFormValid(): boolean {
    return this.isRoomTypeNameValid() && this.isMaxOccupancyValid() && this.isTotalRoomsValid();
  }

  submitCreateRoomType(): void {
    this.roomTypeTouched = {
      roomTypeName: true,
      maxOccupancy: true,
      totalRooms: true
    };
    this.roomTypeFormError = '';
    this.roomTypeFormSuccess = '';

    if (!this.selectedHotelForRoom && this.selectedHotel) {
      this.selectedHotelForRoom = this.selectedHotel;
    }
    if (!this.selectedHotelForRoom) {
      this.roomTypeFormError = 'No hotel property selected. Please select a property.';
      return;
    }

    if (!this.isRoomTypeFormValid()) {
      if (!this.newRoomType.roomTypeName.trim()) {
        this.roomTypeFormError = 'Room Type Name is required.';
      } else if (this.isDuplicateRoomTypeName(this.newRoomType.roomTypeName)) {
        this.roomTypeFormError = `Room category '${this.newRoomType.roomTypeName.trim()}' already exists for this property.`;
      } else if (!this.isMaxOccupancyValid()) {
        this.roomTypeFormError = 'Maximum occupancy must be between 1 and 20 guests.';
      } else if (!this.isTotalRoomsValid()) {
        this.roomTypeFormError = 'Total rooms count must be at least 1.';
      }
      return;
    }

    this.isSubmittingRoomType = true;

    const payload: RoomTypeCreateRequest = {
      roomTypeName: this.newRoomType.roomTypeName.trim(),
      maxOccupancy: this.newRoomType.maxOccupancy,
      totalRooms: Number(this.newRoomType.totalRooms),
      amenities: this.newRoomType.amenities.length > 0 ? this.newRoomType.amenities.join(', ') : undefined,
      imageUrl: this.newRoomType.imageUrl ? this.newRoomType.imageUrl : undefined
    };

    this.hotelService.addRoomType(this.selectedHotelForRoom.id, payload).subscribe({
      next: (created) => {
        this.isSubmittingRoomType = false;
        this.roomTypeFormSuccess = `Room type "${created.roomTypeName || payload.roomTypeName}" created successfully!`;
        this.loadDashboardData();
        setTimeout(() => {
          this.roomViewMode = 'list';
          this.roomTypeFormSuccess = '';
        }, 1200);
      },
      error: (err) => {
        this.isSubmittingRoomType = false;
        console.error('Failed to create room type:', err);
        this.roomTypeFormError = err.error?.message || 'Failed to create room type. Please try again.';
      }
    });
  }

  // =========================================================================
  // WP-06: ROOM TYPES LISTING GETTERS & METHODS
  // =========================================================================

  get totalRoomTypesCount(): number {
    return this.roomTypes ? this.roomTypes.length : 0;
  }

  get activeRoomTypesCount(): number {
    if (!this.roomTypes) return 0;
    return this.roomTypes.filter(r => (r.status || 'ACTIVE').toUpperCase() === 'ACTIVE').length;
  }

  get archivedRoomTypesCount(): number {
    if (!this.roomTypes) return 0;
    return this.roomTypes.filter(r => (r.status || '').toUpperCase() === 'ARCHIVED').length;
  }

  get filteredRoomTypes(): any[] {
    if (!this.roomTypes) return [];

    let result = [...this.roomTypes];

    // 1. Status Filter
    if (this.roomListingStatusFilter === 'Active') {
      result = result.filter(r => (r.status || 'ACTIVE').toUpperCase() === 'ACTIVE');
    } else if (this.roomListingStatusFilter === 'Archived') {
      result = result.filter(r => (r.status || '').toUpperCase() === 'ARCHIVED');
    }
    // 'All' includes all statuses

    // 2. Search Query (by room type name, case-insensitive)
    const query = (this.roomListingSearchQuery || '').trim().toLowerCase();
    if (query) {
      result = result.filter(r => (r.roomTypeName || '').toLowerCase().includes(query));
    }

    // 3. Sorting
    switch (this.roomListingSortBy) {
      case 'name-asc':
        result.sort((a, b) => (a.roomTypeName || '').localeCompare(b.roomTypeName || ''));
        break;
      case 'name-desc':
        result.sort((a, b) => (b.roomTypeName || '').localeCompare(a.roomTypeName || ''));
        break;
      case 'rooms-asc':
        result.sort((a, b) => (Number(a.totalRooms) || 0) - (Number(b.totalRooms) || 0));
        break;
      case 'rooms-desc':
        result.sort((a, b) => (Number(b.totalRooms) || 0) - (Number(a.totalRooms) || 0));
        break;
      case 'occupancy-asc':
        result.sort((a, b) => (Number(a.maxOccupancy) || 0) - (Number(b.maxOccupancy) || 0));
        break;
      case 'occupancy-desc':
        result.sort((a, b) => (Number(b.maxOccupancy) || 0) - (Number(a.maxOccupancy) || 0));
        break;
      default:
        result.sort((a, b) => (a.roomTypeName || '').localeCompare(b.roomTypeName || ''));
    }

    return result;
  }

  getRoomAmenitiesList(amenitiesStr?: string): string[] {
    if (!amenitiesStr || !amenitiesStr.trim()) return [];
    return amenitiesStr.split(',').map(s => s.trim()).filter(s => s.length > 0);
  }

  getAmenitiesPreview(amenitiesStr?: string): { displayed: string[]; remainingCount: number } {
    const list = this.getRoomAmenitiesList(amenitiesStr);
    if (list.length <= 3) {
      return { displayed: list, remainingCount: 0 };
    }
    return {
      displayed: list.slice(0, 3),
      remainingCount: list.length - 3
    };
  }

  viewArchivedRoomTypes(): void {
    this.roomViewMode = 'list';
    this.roomListingStatusFilter = 'Archived';
  }

  resetRoomFilters(): void {
    this.roomListingSearchQuery = '';
    this.roomListingStatusFilter = 'Active';
    this.roomListingSortBy = 'name-asc';
  }

  onViewRoomDetails(room: any): void {
    if (!room) return;
    this.selectedRoomDetails = { ...room };
    this.showRoomDetailsModal = true;
    this.roomDetailsError = '';
    document.body.style.overflow = 'hidden';

    // Fetch fresh details from backend to ensure data integrity
    if (room.id) {
      this.isLoadingRoomDetails = true;
      this.hotelService.getRoomType(room.id).subscribe({
        next: (freshRoom) => {
          this.selectedRoomDetails = freshRoom;
          this.isLoadingRoomDetails = false;
        },
        error: (err) => {
          this.isLoadingRoomDetails = false;
          console.warn('Could not fetch fresh room details from backend, retaining local data', err);
        }
      });
    }
  }

  closeRoomDetailsModal(): void {
    this.showRoomDetailsModal = false;
    this.selectedRoomDetails = null;
    this.isLoadingRoomDetails = false;
    this.roomDetailsError = '';
    document.body.style.overflow = '';
  }

  formatRoomTypeId(id: any): string {
    if (id === null || id === undefined || id === '') return 'RT-0000';
    const num = Number(id);
    if (!isNaN(num)) {
      return `RT-${num.toString().padStart(4, '0')}`;
    }
    const str = String(id).trim();
    if (str.toUpperCase().startsWith('RT-')) return str;
    return `RT-${str}`;
  }

  onEditRoomFromModal(room: any): void {
    const targetRoom = room || this.selectedRoomDetails;
    this.closeRoomDetailsModal();
    if (targetRoom) {
      this.startEditRoomType(targetRoom);
    }
  }

  onEditRoom(room: any): void {
    if (room) {
      this.startEditRoomType(room);
    }
  }

  // =========================================================================
  // WP-06: EDIT ROOM TYPE METHODS
  // =========================================================================
  startEditRoomType(room: any): void {
    if (!room) return;
    this.closeRoomDetailsModal();
    this.editingRoomType = {
      id: room.id,
      hotelId: room.hotelId || (this.selectedHotel ? this.selectedHotel.id : null),
      roomTypeName: room.roomTypeName || '',
      maxOccupancy: Number(room.maxOccupancy) || 2,
      totalRooms: Number(room.totalRooms) || 1,
      amenitiesList: this.getRoomAmenitiesList(room.amenities),
      imageUrl: room.imageUrl || '',
      status: room.status || 'ACTIVE',
      pricePerNight: room.pricePerNight
    };
    this.originalRoomType = JSON.parse(JSON.stringify(this.editingRoomType));
    this.editRoomImagePreview = room.imageUrl || null;
    this.editRoomImageFile = null;
    this.isEditRoomImageRemoved = false;
    this.editRoomImageError = '';
    this.editRoomTypeFormError = '';
    this.editRoomTypeFormSuccess = '';
    this.editRoomTypeTouched = {};
    this.editCustomAmenityInput = '';
    this.editRoomCustomAmenityError = '';
    this.roomViewMode = 'edit';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEditRoomType(): void {
    if (this.hasEditFormChanges()) {
      if (!confirm('You have unsaved changes. Discard changes and return to room listing?')) {
        return;
      }
    }
    this.roomViewMode = 'list';
    this.editingRoomType = null;
    this.originalRoomType = null;
    this.editRoomTypeFormError = '';
    this.editRoomTypeFormSuccess = '';
    this.editRoomImagePreview = null;
    this.editRoomImageFile = null;
    this.isEditRoomImageRemoved = false;
  }

  hasEditFormChanges(): boolean {
    if (!this.editingRoomType || !this.originalRoomType) return false;
    if ((this.editingRoomType.roomTypeName || '').trim() !== (this.originalRoomType.roomTypeName || '').trim()) return true;
    if (Number(this.editingRoomType.maxOccupancy) !== Number(this.originalRoomType.maxOccupancy)) return true;
    if (Number(this.editingRoomType.totalRooms) !== Number(this.originalRoomType.totalRooms)) return true;
    if (this.isEditRoomImageRemoved) return true;
    if (this.editRoomImagePreview !== this.originalRoomType.imageUrl) return true;
    const currentList = [...(this.editingRoomType.amenitiesList || [])].sort().join(',');
    const originalList = [...(this.originalRoomType.amenitiesList || [])].sort().join(',');
    if (currentList !== originalList) return true;
    return false;
  }

  incrementEditOccupancy(): void {
    if (!this.editingRoomType) return;
    const cur = Number(this.editingRoomType.maxOccupancy) || 1;
    if (cur < 20) {
      this.editingRoomType.maxOccupancy = cur + 1;
    }
  }

  decrementEditOccupancy(): void {
    if (!this.editingRoomType) return;
    const cur = Number(this.editingRoomType.maxOccupancy) || 2;
    if (cur > 1) {
      this.editingRoomType.maxOccupancy = cur - 1;
    }
  }

  onEditOccupancyChange(): void {
    if (!this.editingRoomType) return;
    let val = Number(this.editingRoomType.maxOccupancy);
    if (isNaN(val) || val < 1) val = 1;
    if (val > 20) val = 20;
    this.editingRoomType.maxOccupancy = val;
  }

  incrementEditTotalRooms(): void {
    if (!this.editingRoomType) return;
    const cur = Number(this.editingRoomType.totalRooms) || 1;
    this.editingRoomType.totalRooms = cur + 1;
  }

  decrementEditTotalRooms(): void {
    if (!this.editingRoomType) return;
    const cur = Number(this.editingRoomType.totalRooms) || 1;
    if (cur > 1) {
      this.editingRoomType.totalRooms = cur - 1;
    }
  }

  onEditTotalRoomsChange(): void {
    if (!this.editingRoomType) return;
    let val = Number(this.editingRoomType.totalRooms);
    if (isNaN(val) || val < 1) val = 1;
    this.editingRoomType.totalRooms = val;
  }

  isEditAmenitySelected(amenityName: string): boolean {
    if (!this.editingRoomType || !this.editingRoomType.amenitiesList) return false;
    return this.editingRoomType.amenitiesList.includes(amenityName);
  }

  toggleEditAmenity(amenityName: string): void {
    if (!this.editingRoomType) return;
    if (!this.editingRoomType.amenitiesList) {
      this.editingRoomType.amenitiesList = [];
    }
    const idx = this.editingRoomType.amenitiesList.indexOf(amenityName);
    if (idx > -1) {
      this.editingRoomType.amenitiesList.splice(idx, 1);
    } else {
      this.editingRoomType.amenitiesList.push(amenityName);
    }
  }

  addEditCustomAmenity(): void {
    this.editRoomCustomAmenityError = '';
    if (!this.editingRoomType) return;
    if (!this.editingRoomType.amenitiesList) {
      this.editingRoomType.amenitiesList = [];
    }
    const trimmed = (this.editCustomAmenityInput || '').trim();
    if (!trimmed) {
      this.editRoomCustomAmenityError = 'Please enter an amenity name.';
      return;
    }
    if (trimmed.length > 50) {
      this.editRoomCustomAmenityError = 'Amenity name cannot exceed 50 characters.';
      return;
    }
    if (trimmed.includes(',')) {
      this.editRoomCustomAmenityError = 'Amenity name cannot contain commas.';
      return;
    }
    const exists = this.editingRoomType.amenitiesList.some(
      (a: string) => a.toLowerCase() === trimmed.toLowerCase()
    );
    if (exists) {
      this.editRoomCustomAmenityError = 'This amenity has already been added.';
      return;
    }
    this.editingRoomType.amenitiesList.push(trimmed);
    this.editCustomAmenityInput = '';
  }

  removeEditAmenity(amenityName: string): void {
    if (!this.editingRoomType || !this.editingRoomType.amenitiesList) return;
    this.editingRoomType.amenitiesList = this.editingRoomType.amenitiesList.filter(
      (a: string) => a !== amenityName
    );
  }

  onEditRoomImageSelected(event: any): void {
    const file = event.target?.files?.[0];
    if (file) {
      this.processEditRoomImageFile(file);
    }
  }

  onEditRoomImageDrop(event: DragEvent): void {
    event.preventDefault();
    this.isEditRoomImageDragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.processEditRoomImageFile(file);
    }
  }

  onEditRoomImageDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isEditRoomImageDragOver = true;
  }

  onEditRoomImageDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isEditRoomImageDragOver = false;
  }

  processEditRoomImageFile(file: File): void {
    this.editRoomImageError = '';
    const allowed = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type.toLowerCase())) {
      this.editRoomImageError = 'Supported formats: JPG, JPEG, PNG, WebP.';
      return;
    }
    if (file.size > 1048576) {
      this.editRoomImageError = 'Image file size must be ≤ 1 MB.';
      return;
    }

    this.editRoomImageFile = file;
    this.isUploadingEditRoomImage = true;

    const propertyId = this.selectedHotel?.id || this.editingRoomType?.hotelId;
    if (propertyId) {
      this.hotelService.uploadHotelImage(file, propertyId).subscribe({
        next: (res) => {
          this.isUploadingEditRoomImage = false;
          this.editRoomImagePreview = res.imageUrl;
          this.editingRoomType.imageUrl = res.imageUrl;
          this.isEditRoomImageRemoved = false;
        },
        error: (err) => {
          this.isUploadingEditRoomImage = false;
          console.warn('Upload image API failed, falling back to local FileReader preview:', err);
          const reader = new FileReader();
          reader.onload = (e: any) => {
            this.editRoomImagePreview = e.target.result;
            this.isEditRoomImageRemoved = false;
          };
          reader.readAsDataURL(file);
        }
      });
    } else {
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.isUploadingEditRoomImage = false;
        this.editRoomImagePreview = e.target.result;
        this.isEditRoomImageRemoved = false;
      };
      reader.readAsDataURL(file);
    }
  }

  removeEditRoomImage(): void {
    this.editRoomImagePreview = null;
    this.editRoomImageFile = null;
    if (this.editingRoomType) {
      this.editingRoomType.imageUrl = '';
    }
    this.isEditRoomImageRemoved = true;
    this.editRoomImageError = '';
  }

  isEditDuplicateRoomTypeName(name?: string): boolean {
    const trimmed = (name || this.editingRoomType?.roomTypeName || '').trim().toLowerCase();
    if (!trimmed || !this.editingRoomType) return false;
    const rooms = this.roomTypes || [];
    return rooms.some((r: any) => {
      if (r.id === this.editingRoomType.id) return false;
      const status = (r.status || 'ACTIVE').toUpperCase();
      return status === 'ACTIVE' && (r.roomTypeName || '').trim().toLowerCase() === trimmed;
    });
  }

  isEditRoomTypeNameValid(): boolean {
    if (!this.editingRoomType || !this.editingRoomType.roomTypeName) return false;
    const trimmed = this.editingRoomType.roomTypeName.trim();
    if (!trimmed || trimmed.length > 100) return false;
    if (this.isEditDuplicateRoomTypeName(trimmed)) return false;
    return true;
  }

  isEditOccupancyValid(): boolean {
    if (!this.editingRoomType) return false;
    const num = Number(this.editingRoomType.maxOccupancy);
    return !isNaN(num) && num >= 1 && num <= 20;
  }

  isEditTotalRoomsValid(): boolean {
    if (!this.editingRoomType) return false;
    const num = Number(this.editingRoomType.totalRooms);
    return !isNaN(num) && num >= 1;
  }

  isEditFormValid(): boolean {
    return this.isEditRoomTypeNameValid() && this.isEditOccupancyValid() && this.isEditTotalRoomsValid();
  }

  submitEditRoomType(): void {
    if (!this.editingRoomType) return;
    this.editRoomTypeTouched = { roomTypeName: true, maxOccupancy: true, totalRooms: true };
    if (!this.isEditFormValid()) {
      this.editRoomTypeFormError = 'Please correct the highlighted errors before saving.';
      return;
    }

    this.isSubmittingEditRoomType = true;
    this.editRoomTypeFormError = '';
    this.editRoomTypeFormSuccess = '';

    const payload: any = {
      roomTypeName: this.editingRoomType.roomTypeName.trim(),
      maxOccupancy: Number(this.editingRoomType.maxOccupancy),
      totalRooms: Number(this.editingRoomType.totalRooms),
      amenities: (this.editingRoomType.amenitiesList || []).join(', ')
    };

    if (this.isEditRoomImageRemoved) {
      payload.imageUrl = '';
    } else if (this.editingRoomType.imageUrl) {
      payload.imageUrl = this.editingRoomType.imageUrl;
    }

    this.hotelService.updateRoomType(this.editingRoomType.id, payload).subscribe({
      next: (updatedRoom: any) => {
        this.isSubmittingEditRoomType = false;
        const name = updatedRoom.roomTypeName || this.editingRoomType.roomTypeName;
        this.editRoomTypeFormSuccess = `Room type "${name}" has been updated successfully.`;

        // Update local arrays for instant feedback
        if (this.roomTypes) {
          const idx = this.roomTypes.findIndex((r: any) => r.id === updatedRoom.id);
          if (idx > -1) {
            this.roomTypes[idx] = { ...this.roomTypes[idx], ...updatedRoom };
          }
        }

        this.loadDashboardData();

        setTimeout(() => {
          this.roomViewMode = 'list';
          this.editRoomTypeFormSuccess = '';
          this.editingRoomType = null;
          this.originalRoomType = null;
        }, 1200);
      },
      error: (err: any) => {
        this.isSubmittingEditRoomType = false;
        console.error('Failed to update room type:', err);
        this.editRoomTypeFormError = err.error?.message || 'Failed to update room type. Please review your input and try again.';
      }
    });
  }

  openArchiveRoomModal(room: any): void {
    this.roomToArchive = room;
    this.archiveRoomError = '';
    this.showArchiveRoomModal = true;
  }

  cancelArchiveRoom(): void {
    this.showArchiveRoomModal = false;
    this.roomToArchive = null;
    this.archiveRoomError = '';
  }

  confirmArchiveRoom(): void {
    if (!this.roomToArchive || !this.roomToArchive.id) return;

    this.isArchivingRoom = true;
    this.archiveRoomError = '';

    this.hotelService.archiveRoomType(this.roomToArchive.id).subscribe({
      next: () => {
        this.isArchivingRoom = false;
        const archivedName = this.roomToArchive.roomTypeName;
        this.showArchiveRoomModal = false;
        this.roomToArchive = null;
        this.archiveRoomSuccess = `Room type "${archivedName}" has been successfully archived.`;

        // Reload property room types to get updated status and counts
        this.loadDashboardData();

        setTimeout(() => {
          this.archiveRoomSuccess = '';
        }, 4000);
      },
      error: (err) => {
        this.isArchivingRoom = false;
        console.error('Failed to archive room type:', err);
        this.archiveRoomError = err.error?.message || 'Failed to archive room type. Please ensure no active reservations exist for this category.';
      }
    });
  }

  // =========================================================================
  // WP-06: RESTORE ARCHIVED ROOM TYPE METHODS
  // =========================================================================
  openRestoreRoomModal(room: any): void {
    this.roomToRestore = room;
    this.restoreRoomError = '';
    this.showRestoreRoomModal = true;
  }

  cancelRestoreRoom(): void {
    this.showRestoreRoomModal = false;
    this.roomToRestore = null;
    this.restoreRoomError = '';
  }

  onRestoreRoomFromModal(room: any): void {
    const targetRoom = room || this.selectedRoomDetails;
    this.closeRoomDetailsModal();
    if (targetRoom) {
      this.openRestoreRoomModal(targetRoom);
    }
  }

  confirmRestoreRoom(): void {
    if (!this.roomToRestore || !this.roomToRestore.id) return;

    this.isRestoringRoom = true;
    this.restoreRoomError = '';

    this.hotelService.restoreRoomType(this.roomToRestore.id).subscribe({
      next: (res: any) => {
        this.isRestoringRoom = false;
        const restoredName = this.roomToRestore.roomTypeName;
        const restoredId = this.roomToRestore.id;
        this.showRestoreRoomModal = false;
        this.roomToRestore = null;
        this.restoreRoomSuccess = `Room type "${restoredName}" has been successfully restored to Active.`;

        // Update local room status immediately for instant feedback
        if (this.roomTypes) {
          const target = this.roomTypes.find((r: any) => r.id === (res?.roomId || restoredId));
          if (target) {
            target.status = 'ACTIVE';
            target.availableRooms = target.totalRooms;
          }
        }

        // Reload property room types to get updated status and counts
        this.loadDashboardData();

        setTimeout(() => {
          this.restoreRoomSuccess = '';
        }, 4000);
      },
      error: (err) => {
        this.isRestoringRoom = false;
        console.error('Failed to restore room type:', err);
        this.restoreRoomError = err.error?.message || 'Failed to restore room type. Please ensure no active room type with the same name exists.';
      }
    });
  }

  // =========================================================================
  // WP-07.2: BASE PRICE MANAGEMENT METHODS
  // =========================================================================

  refreshBasePriceListing(): void {
    const activeRooms = (this.roomTypes || []).filter((r: any) => (r.status || 'ACTIVE').toUpperCase() === 'ACTIVE');

    if (activeRooms.length > 0) {
      const prices = activeRooms.map((r: any) => Number(r.pricePerNight) || 0);
      this.highestBasePrice = Math.max(...prices);
      this.lowestBasePrice = Math.min(...prices);
    } else {
      this.highestBasePrice = 0;
      this.lowestBasePrice = 0;
    }

    let filtered = [...activeRooms];
    if (this.basePriceSearchQuery && this.basePriceSearchQuery.trim()) {
      const q = this.basePriceSearchQuery.trim().toLowerCase();
      filtered = filtered.filter((r: any) => (r.roomTypeName || '').toLowerCase().includes(q));
    }

    filtered.sort((a: any, b: any) => {
      switch (this.basePriceSortOption) {
        case 'name-desc':
          return (b.roomTypeName || '').localeCompare(a.roomTypeName || '');
        case 'price-asc':
          return (Number(a.pricePerNight) || 0) - (Number(b.pricePerNight) || 0);
        case 'price-desc':
          return (Number(b.pricePerNight) || 0) - (Number(a.pricePerNight) || 0);
        case 'name-asc':
        default:
          return (a.roomTypeName || '').localeCompare(b.roomTypeName || '');
      }
    });

    this.displayedBasePriceRooms = filtered;
  }

  onBasePriceSearchChange(): void {
    this.refreshBasePriceListing();
  }

  onBasePriceSortChange(): void {
    this.refreshBasePriceListing();
  }

  resetBasePriceSearch(): void {
    this.basePriceSearchQuery = '';
    this.refreshBasePriceListing();
  }

  getRoomSubtitle(room: any): string {
    if (room.description) return room.description;
    const name = (room.roomTypeName || '').toLowerCase();
    if (name.includes('double')) return 'Spacious room with city view';
    if (name.includes('twin')) return 'Comfortable twin beds with modern amenities';
    if (name.includes('executive') || name.includes('suite')) return 'Large suite with a separate seating area';
    if (name.includes('family')) return 'Ideal for families with extra space';
    if (room.amenities) {
      const list = room.amenities.split(',').map((s: string) => s.trim()).filter(Boolean);
      if (list.length > 0) return list.slice(0, 3).join(' • ');
    }
    return `Spacious room configured for ${room.maxOccupancy || 2} guests`;
  }

  openEditBasePriceModal(room: any): void {
    if (!room || (room.status && room.status.toUpperCase() === 'ARCHIVED')) return;
    this.selectedRoomForPrice = { ...room };
    this.activeEditPriceImage = room.imageUrl || '';
    this.editBasePriceValue = room.pricePerNight !== undefined && room.pricePerNight !== null ? Number(room.pricePerNight) : 0;
    this.editBasePriceInputError = '';
    this.editBasePriceError = '';
    this.isUpdatingBasePrice = false;
    this.showEditBasePriceModal = true;
    document.body.style.overflow = 'hidden';
  }

  closeEditBasePriceModal(): void {
    this.showEditBasePriceModal = false;
    this.selectedRoomForPrice = null;
    this.activeEditPriceImage = '';
    this.editBasePriceValue = null;
    this.editBasePriceInputError = '';
    this.editBasePriceError = '';
    this.isUpdatingBasePrice = false;
    document.body.style.overflow = '';
  }

  getBasePriceModalThumbnails(): string[] {
    const images: string[] = [];
    if (this.selectedRoomForPrice?.imageUrl) {
      images.push(this.selectedRoomForPrice.imageUrl);
    }
    if (this.selectedHotel?.imageUrl && !images.includes(this.selectedHotel.imageUrl)) {
      images.push(this.selectedHotel.imageUrl);
    }
    if (images.length === 1) {
      images.push('https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80');
    } else if (images.length === 0) {
      images.push('https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=800&q=80');
      images.push('https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80');
    }
    return images.slice(0, 2);
  }

  validateBasePriceInput(): boolean {
    if (this.editBasePriceValue === null || this.editBasePriceValue === undefined || (this.editBasePriceValue as any) === '') {
      this.editBasePriceInputError = 'Base price is required.';
      return false;
    }
    const val = Number(this.editBasePriceValue);
    if (isNaN(val) || !isFinite(val) || val <= 0) {
      this.editBasePriceInputError = 'Base price must be a valid positive number greater than zero.';
      return false;
    }
    this.editBasePriceInputError = '';
    return true;
  }

  submitUpdateBasePrice(): void {
    if (!this.validateBasePriceInput()) return;
    if (!this.selectedRoomForPrice || !this.selectedRoomForPrice.id) return;

    this.isUpdatingBasePrice = true;
    this.editBasePriceError = '';

    const roomId = this.selectedRoomForPrice.id;
    const newPrice = Number(this.editBasePriceValue);

    this.hotelService.updateBasePrice(roomId, newPrice).subscribe({
      next: (updatedRoom: any) => {
        this.isUpdatingBasePrice = false;

        // Update local room type in roomTypes list
        const target = this.roomTypes.find((r: any) => r.id === roomId);
        if (target) {
          target.pricePerNight = updatedRoom.pricePerNight !== undefined ? updatedRoom.pricePerNight : newPrice;
        }

        const roomName = updatedRoom.roomTypeName || this.selectedRoomForPrice.roomTypeName || 'Room';
        const formattedPrice = this.formatCurrency(newPrice);

        this.closeEditBasePriceModal();
        this.refreshBasePriceListing();

        this.basePriceUpdateSuccess = `Base price for "${roomName}" successfully updated to ${formattedPrice} / night.`;
        setTimeout(() => {
          this.basePriceUpdateSuccess = '';
        }, 4000);
      },
      error: (err: any) => {
        this.isUpdatingBasePrice = false;
        this.editBasePriceError = err.error?.message || 'Failed to update base price. Please verify your inputs and try again.';
      }
    });
  }

  openAddRoomModal(hotel?: any): void {
    this.selectedHotelForRoom = hotel || this.selectedHotel;
    if (!this.selectedHotelForRoom && this.myHotels.length > 0) {
      this.selectedHotelForRoom = this.myHotels[0];
    }
    if (!this.selectedHotelForRoom) {
      alert('Please register or select a hotel property before adding room types.');
      return;
    }
    this.showAddRoomModal = true;
  }

  submitAddRoomType(): void {
    if (!this.selectedHotelForRoom) return;
    if (!this.roomTypeName.trim()) {
      alert('Please enter a room category name.');
      return;
    }

    const payload = {
      roomTypeName: this.roomTypeName.trim(),
      maxOccupancy: this.roomMaxOccupancy,
      pricePerNight: this.roomPricePerNight,
      totalRooms: this.roomTotalCount,
      availableRooms: this.roomTotalCount,
      amenities: this.roomAmenities
    };

    this.hotelService.addRoomType(this.selectedHotelForRoom.id, payload).subscribe({
      next: () => {
        alert('Room Type added successfully!');
        this.showAddRoomModal = false;
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Add room type error:', err);
        alert(err.error?.message || 'Failed to add room type.');
      }
    });
  }

  // ==================== WP-07.3: ROOM BLOCKS MANAGEMENT IMPLEMENTATION ====================

  selectInventorySubNav(tab: 'inventory-calendar' | 'pricing-calendar' | 'base-price' | 'room-blocks'): void {
    this.inventorySubNav = tab;
    if (tab === 'room-blocks') {
      this.refreshRoomBlocksListing();
    } else if (tab === 'base-price') {
      this.refreshBasePriceListing();
    }
  }

  refreshRoomBlocksListing(): void {
    const blocks = this.roomBlocks || [];
    this.roomBlocksList = blocks;

    // Property-wide summary counts
    this.totalBlocksCount = blocks.length;
    this.activeBlocksCount = blocks.filter(b => (b.status || '').toUpperCase() === 'ACTIVE').length;
    this.releasedBlocksCount = blocks.filter(b => (b.status || '').toUpperCase() === 'RELEASED').length;

    this.applyRoomBlockFilters();
  }

  applyRoomBlockFilters(): void {
    let filtered = [...this.roomBlocksList];

    // 1. Search Query (Block ID, reason, notes, room type name)
    if (this.blockSearchQuery && this.blockSearchQuery.trim()) {
      const q = this.blockSearchQuery.trim().toLowerCase();
      filtered = filtered.filter(b => {
        const blockCode = (b.blockCode || this.formatBlockId(b.id)).toLowerCase();
        const reason = (b.reason || '').toLowerCase();
        const reasonDisplay = this.getReasonDisplay(b.reason).toLowerCase();
        const notes = (b.notes || '').toLowerCase();
        const roomName = this.getRoomNameForBlock(b).toLowerCase();
        return blockCode.includes(q) ||
               reason.includes(q) ||
               reasonDisplay.includes(q) ||
               notes.includes(q) ||
               roomName.includes(q);
      });
    }

    // 2. Room Type Filter
    if (this.selectedBlockRoomTypeFilter && this.selectedBlockRoomTypeFilter !== 'all') {
      const rId = Number(this.selectedBlockRoomTypeFilter);
      filtered = filtered.filter(b => Number(b.roomId) === rId);
    }

    // 3. Status Filter
    if (this.selectedBlockStatusFilter && this.selectedBlockStatusFilter !== 'all') {
      filtered = filtered.filter(b => (b.status || '').toUpperCase() === this.selectedBlockStatusFilter.toUpperCase());
    }

    // 4. Date Range Overlap Filter [filterStart, filterEnd)
    // Overlap condition: blockStart < filterEnd && blockEnd > filterStart
    if (this.blockDateFilterStart && this.blockDateFilterEnd) {
      try {
        const fStart = new Date(this.blockDateFilterStart);
        const fEnd = new Date(this.blockDateFilterEnd);
        filtered = filtered.filter(b => {
          if (!b.startDate || !b.endDate) return false;
          const bStart = new Date(b.startDate);
          const bEnd = new Date(b.endDate);
          return (bStart < fEnd && bEnd > fStart);
        });
      } catch (e) {
        console.warn('Date filter error:', e);
      }
    }

    this.totalMatchingBlocks = filtered.length;
    this.totalBlocksPages = Math.max(1, Math.ceil(this.totalMatchingBlocks / this.blocksPageSize));

    if (this.blocksCurrentPage > this.totalBlocksPages) {
      this.blocksCurrentPage = 1;
    }

    const startIndex = (this.blocksCurrentPage - 1) * this.blocksPageSize;
    this.displayedRoomBlocks = filtered.slice(startIndex, startIndex + this.blocksPageSize);
  }

  onBlockSearchChange(): void {
    this.blocksCurrentPage = 1;
    this.applyRoomBlockFilters();
  }

  onBlockRoomTypeFilterChange(): void {
    this.blocksCurrentPage = 1;
    this.applyRoomBlockFilters();
  }

  onBlockStatusFilterChange(): void {
    this.blocksCurrentPage = 1;
    this.applyRoomBlockFilters();
  }

  toggleBlockDateFilterDropdown(): void {
    this.showBlockDateFilterDropdown = !this.showBlockDateFilterDropdown;
  }

  applyBlockDateFilter(): void {
    this.showBlockDateFilterDropdown = false;
    this.blocksCurrentPage = 1;
    this.applyRoomBlockFilters();
  }

  clearBlockDateFilter(): void {
    this.blockDateFilterStart = '';
    this.blockDateFilterEnd = '';
    this.showBlockDateFilterDropdown = false;
    this.blocksCurrentPage = 1;
    this.applyRoomBlockFilters();
  }

  hasActiveBlockDateFilter(): boolean {
    return !!(this.blockDateFilterStart && this.blockDateFilterEnd);
  }

  getBlockDateFilterLabel(): string {
    if (this.blockDateFilterStart && this.blockDateFilterEnd) {
      return `${this.formatBlockDate(this.blockDateFilterStart)} - ${this.formatBlockDate(this.blockDateFilterEnd)}`;
    }
    return 'Select date range';
  }

  // Pagination controls
  goToBlocksPage(page: number): void {
    if (page >= 1 && page <= this.totalBlocksPages) {
      this.blocksCurrentPage = page;
      this.closeBlockMenu();
      this.applyRoomBlockFilters();
    }
  }

  prevBlocksPage(): void {
    if (this.blocksCurrentPage > 1) {
      this.goToBlocksPage(this.blocksCurrentPage - 1);
    }
  }

  nextBlocksPage(): void {
    if (this.blocksCurrentPage < this.totalBlocksPages) {
      this.goToBlocksPage(this.blocksCurrentPage + 1);
    }
  }

  getBlocksPaginationPages(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(1, this.blocksCurrentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalBlocksPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
  }

  // Floating Action Menu controls
  toggleBlockMenu(blockId: number, event: Event): void {
    event.stopPropagation();
    this.activeBlockMenuId = this.activeBlockMenuId === blockId ? null : blockId;
  }

  closeBlockMenu(): void {
    this.activeBlockMenuId = null;
  }

  // Create Room Block Modal Workflow (Reference 2)
  openCreateBlockModal(): void {
    if (!this.selectedHotel) {
      alert('Please select a hotel property first.');
      return;
    }

    // Only ACTIVE room types belonging to selected hotel are eligible (Global Business Rule 3.2)
    this.activeRoomTypesForBlock = (this.roomTypes || []).filter(
      rt => (!rt.status || rt.status.toUpperCase() === 'ACTIVE') &&
            (!rt.hotelId || Number(rt.hotelId) === Number(this.selectedHotel.id))
    );

    // Initial state: no room type selected
    this.createBlockSelectedRoomId = null;
    this.selectedRoomTypeForBlock = null;
    this.createBlockMainImage = '';
    this.createBlockThumbnails = [];
    this.createBlockActiveThumbIndex = 0;
    this.createBlockCount = 1;
    this.createBlockReason = 'OPERATIONAL';
    this.createBlockNotes = '';
    this.createBlockError = '';
    this.isCreatingBlock = false;

    // Date range initially empty for user selection matching mockup
    this.createBlockStartDate = '';
    this.createBlockEndDate = '';
    this.showCreateBlockDateDropdown = false;

    this.closeBlockMenu();
    this.showCreateBlockModal = true;
  }

  closeCreateBlockModal(): void {
    this.showCreateBlockModal = false;
    this.showCreateBlockDateDropdown = false;
    this.createBlockError = '';
    this.isCreatingBlock = false;
  }

  toggleCreateBlockDateDropdown(): void {
    this.showCreateBlockDateDropdown = !this.showCreateBlockDateDropdown;
  }

  applyCreateBlockDateRange(): void {
    this.showCreateBlockDateDropdown = false;
  }

  clearCreateBlockDateRange(): void {
    this.createBlockStartDate = '';
    this.createBlockEndDate = '';
    this.showCreateBlockDateDropdown = false;
  }

  getCreateBlockDateLabel(): string {
    if (this.createBlockStartDate && this.createBlockEndDate) {
      return `${this.formatBlockDate(this.createBlockStartDate)} - ${this.formatBlockDate(this.createBlockEndDate)}`;
    }
    if (this.createBlockStartDate) {
      return `${this.formatBlockDate(this.createBlockStartDate)} - ...`;
    }
    return 'Select date ran...';
  }

  onSelectRoomTypeForBlock(event: any): void {
    const val = event?.target?.value || event;
    const roomId = Number(val);
    if (!roomId) {
      this.createBlockSelectedRoomId = null;
      this.selectedRoomTypeForBlock = null;
      this.createBlockMainImage = '';
      this.createBlockThumbnails = [];
      return;
    }

    this.createBlockSelectedRoomId = roomId;
    const rt = this.activeRoomTypesForBlock.find(r => r.id === roomId);
    this.selectedRoomTypeForBlock = rt || null;

    if (rt) {
      this.createBlockMainImage = rt.imageUrl || 'https://images.unsplash.com/photo-1590490360182-c33d57733427';
      if (rt.images && rt.images.length > 0) {
        this.createBlockThumbnails = rt.images.map((img: any) => typeof img === 'string' ? img : img.imageUrl).filter(Boolean);
      } else {
        this.createBlockThumbnails = [this.createBlockMainImage];
      }
      this.createBlockActiveThumbIndex = 0;
    }
  }

  selectCreateBlockThumb(index: number, imgUrl: string): void {
    this.createBlockActiveThumbIndex = index;
    this.createBlockMainImage = imgUrl;
  }

  incrementCreateBlockCount(): void {
    this.createBlockCount = (Number(this.createBlockCount) || 1) + 1;
  }

  decrementCreateBlockCount(): void {
    if (this.createBlockCount > 1) {
      this.createBlockCount = (Number(this.createBlockCount) || 1) - 1;
    }
  }

  submitCreateBlock(): void {
    if (!this.selectedHotel || !this.createBlockSelectedRoomId) {
      this.createBlockError = 'Please select an eligible active room type.';
      return;
    }

    if (!this.createBlockStartDate || !this.createBlockEndDate) {
      this.createBlockError = 'Please select both start date and end date.';
      return;
    }

    if (new Date(this.createBlockEndDate) <= new Date(this.createBlockStartDate)) {
      this.createBlockError = 'End date must be strictly after start date (exclusive end date).';
      return;
    }

    if (!this.createBlockCount || this.createBlockCount < 1) {
      this.createBlockError = 'Blocked room count must be at least 1.';
      return;
    }

    if (!this.createBlockReason) {
      this.createBlockError = 'A blocking reason is mandatory.';
      return;
    }

    if (this.createBlockNotes && this.createBlockNotes.trim().length > 250) {
      this.createBlockError = 'Notes cannot exceed 250 characters.';
      return;
    }

    this.isCreatingBlock = true;
    this.createBlockError = '';

    const payload = {
      blockedCount: Number(this.createBlockCount),
      startDate: this.createBlockStartDate,
      endDate: this.createBlockEndDate,
      reason: this.createBlockReason,
      notes: this.createBlockNotes ? this.createBlockNotes.trim() : null
    };

    this.hotelService.createRoomBlock(this.selectedHotel.id, this.createBlockSelectedRoomId, payload).subscribe({
      next: (created) => {
        this.isCreatingBlock = false;
        this.showCreateBlockModal = false;
        const code = this.formatBlockId(created.id);
        this.roomBlocksSuccess = `Room block #${code} successfully created for ${this.createBlockCount} room(s).`;
        setTimeout(() => this.roomBlocksSuccess = '', 4500);
        this.reloadRoomBlocks();
      },
      error: (err: any) => {
        this.isCreatingBlock = false;
        console.error('Create room block error:', err);
        this.createBlockError = err.error?.message || (err.status === 400 ? 'Insufficient available inventory for one or more dates in the selected period. Please review the quantity or date range.' : 'Failed to create room block. Please try again.');
      }
    });
  }

  // Room Block Details Modal Workflow (Reference 3)
  openBlockDetailsModal(block: any): void {
    if (!block) return;
    this.selectedBlockForDetails = block;

    const room = (this.roomTypes || []).find(rt => rt.id === block.roomId);
    this.blockDetailsMainImage = block.imageUrl || room?.imageUrl || 'https://images.unsplash.com/photo-1590490360182-c33d57733427';
    if (room?.images && room.images.length > 0) {
      this.blockDetailsThumbnails = room.images.map((img: any) => typeof img === 'string' ? img : img.imageUrl).filter(Boolean);
    } else {
      this.blockDetailsThumbnails = [this.blockDetailsMainImage];
    }
    this.blockDetailsActiveThumbIndex = 0;

    this.closeBlockMenu();
    this.showBlockDetailsModal = true;
  }

  closeBlockDetailsModal(): void {
    this.showBlockDetailsModal = false;
    this.selectedBlockForDetails = null;
  }

  selectBlockDetailsThumb(index: number, imgUrl: string): void {
    this.blockDetailsActiveThumbIndex = index;
    this.blockDetailsMainImage = imgUrl;
  }

  openReleaseFromDetails(): void {
    const block = this.selectedBlockForDetails;
    this.closeBlockDetailsModal();
    if (block && (block.status || '').toUpperCase() === 'ACTIVE') {
      this.openReleaseBlockModal(block);
    }
  }

  // Release Room Block Confirmation Modal Workflow (Reference 4)
  openReleaseBlockModal(block: any): void {
    if (!block || (block.status || '').toUpperCase() === 'RELEASED') return;
    this.blockToRelease = block;
    this.releaseBlockError = '';
    this.isReleasingBlock = false;

    this.closeBlockMenu();
    this.showReleaseBlockModal = true;
  }

  closeReleaseBlockModal(): void {
    this.showReleaseBlockModal = false;
    this.blockToRelease = null;
    this.releaseBlockError = '';
    this.isReleasingBlock = false;
  }

  confirmReleaseBlock(): void {
    if (!this.blockToRelease) return;

    this.isReleasingBlock = true;
    this.releaseBlockError = '';

    const blockId = this.blockToRelease.id;
    const count = this.blockToRelease.blockedCount;
    const code = this.formatBlockId(blockId);

    this.hotelService.releaseRoomBlock(blockId).subscribe({
      next: (released) => {
        this.isReleasingBlock = false;
        this.showReleaseBlockModal = false;
        this.blockToRelease = null;
        this.roomBlocksSuccess = `Room block #${code} released successfully. ${count} room(s) restored to available inventory.`;
        setTimeout(() => this.roomBlocksSuccess = '', 4500);
        this.reloadRoomBlocks();
      },
      error: (err: any) => {
        this.isReleasingBlock = false;
        console.error('Release room block error:', err);
        this.releaseBlockError = err.error?.message || 'Failed to release room block. Please try again.';
      }
    });
  }

  reloadRoomBlocks(): void {
    if (!this.selectedHotel) return;
    this.hotelService.getRoomBlocks(this.selectedHotel.id).subscribe({
      next: (blocks) => {
        this.roomBlocks = blocks || [];
        this.refreshRoomBlocksListing();
      },
      error: (err) => {
        console.warn('Failed to reload room blocks:', err);
      }
    });
  }

  // Display and format helpers
  formatBlockId(id: any): string {
    if (!id && id !== 0) return 'BLK-0000';
    return `BLK-${String(id).padStart(4, '0')}`;
  }

  getRoomNameForBlock(block: any): string {
    if (!block) return 'Room';
    if (block.roomTypeName) return block.roomTypeName;
    const found = (this.roomTypes || []).find(rt => rt.id === block.roomId);
    if (found?.roomTypeName) return found.roomTypeName;
    return `Room #${block.roomId}`;
  }

  getRoomImageForBlock(block: any): string {
    if (block?.imageUrl) return block.imageUrl;
    const found = (this.roomTypes || []).find(rt => rt.id === block?.roomId);
    if (found?.imageUrl) return found.imageUrl;
    return 'https://images.unsplash.com/photo-1590490360182-c33d57733427';
  }

  getRoomTotalForBlock(block: any): number | string {
    if (block?.totalRooms !== undefined && block?.totalRooms !== null) return block.totalRooms;
    const found = (this.roomTypes || []).find(rt => rt.id === block?.roomId);
    return found?.totalRooms ?? '-';
  }

  getRoomOccupancyForBlock(block: any): number | string {
    if (block?.maxOccupancy !== undefined && block?.maxOccupancy !== null) return block.maxOccupancy;
    const found = (this.roomTypes || []).find(rt => rt.id === block?.roomId);
    return found?.maxOccupancy ?? 2;
  }

  getReasonDisplay(reason: string): string {
    if (!reason) return 'Operational';
    switch (reason.toUpperCase()) {
      case 'OPERATIONAL':
        return 'Corporate group booking';
      case 'MAINTENANCE':
        return 'Maintenance (AC service)';
      case 'RENOVATION':
        return 'Renovation';
      case 'OTHER':
        return 'Other';
      default:
        return reason;
    }
  }

  formatBlockDate(dateStr: string | null | undefined): string {
    if (!dateStr) return '-';
    try {
      const parts = dateStr.split('-');
      if (parts.length === 3) {
        const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const m = parseInt(parts[1], 10) - 1;
        const d = parseInt(parts[2], 10);
        const y = parts[0];
        return `${monthNames[m]} ${d < 10 ? '0' + d : d}, ${y}`;
      }
      return this.formatDate(dateStr);
    } catch {
      return dateStr;
    }
  }

  formatBlockDateExclusiveMinusOne(dateStr: string | null | undefined): string {
    if (!dateStr) return '-';
    try {
      const d = new Date(dateStr);
      d.setDate(d.getDate() - 1);
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const day = d.getDate();
      return `${monthNames[d.getMonth()]} ${day < 10 ? '0' + day : day}`;
    } catch {
      return dateStr;
    }
  }

  getUniqueRoomTypesForBlockFilter(): { id: number; name: string }[] {
    const map = new Map<number, string>();
    (this.roomTypes || []).forEach(rt => {
      if (rt.id) map.set(Number(rt.id), rt.roomTypeName || `Room #${rt.id}`);
    });
    (this.roomBlocksList || []).forEach(b => {
      if (b.roomId && !map.has(Number(b.roomId))) {
        map.set(Number(b.roomId), b.roomTypeName || `Room #${b.roomId}`);
      }
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }

  // Legacy fallback stubs
  openBlockRoomModal(): void {
    this.openCreateBlockModal();
  }

  submitBlockRoom(): void {
    this.submitCreateBlock();
  }

  openPriceOverrideModal(): void {
    if (!this.selectedHotel) {
      alert('Please select a hotel property first.');
      return;
    }
    if (this.roomTypes.length === 0) {
      alert('Please add at least one room category before updating pricing.');
      return;
    }
    this.overrideRoomId = this.roomTypes[0].id;
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const dayAfter = new Date();
    dayAfter.setDate(dayAfter.getDate() + 5);
    this.overrideStartDate = this.formatDateIso(tomorrow);
    this.overrideEndDate = this.formatDateIso(dayAfter);
    this.showPriceOverrideModal = true;
  }

  submitPriceOverride(): void {
    if (!this.overrideRoomId || !this.overrideStartDate || !this.overrideEndDate) {
      alert('Please specify a room category and valid date range.');
      return;
    }

    const payload = {
      startDate: this.overrideStartDate,
      endDate: this.overrideEndDate,
      pricePerNight: this.overridePrice
    };

    this.hotelService.setPriceOverrideRange(this.overrideRoomId, payload).subscribe({
      next: () => {
        alert('Price override updated successfully!');
        this.showPriceOverrideModal = false;
        this.loadDashboardData();
      },
      error: (err) => {
        console.error('Set price override error:', err);
        alert(err.error?.message || 'Failed to update price override.');
      }
    });
  }

  // ==================== RESERVATIONS OPERATIONS ====================
  openReservationDetails(res: any): void {
    this.selectedReservation = res;
    this.showReservationDetailsModal = true;
  }

  checkInGuest(res: any): void {
    if (!res?.bookingRef) return;
    this.bookingService.checkInGuest(res.bookingRef).subscribe({
      next: () => {
        alert(`Guest ${res.customerName} checked in successfully!`);
        this.loadDashboardData();
        if (this.selectedReservation) this.selectedReservation.status = 'CHECKED_IN';
      },
      error: (err) => alert(err.error?.message || 'Check-in failed')
    });
  }

  checkOutGuest(res: any): void {
    if (!res?.bookingRef) return;
    this.bookingService.checkOutGuest(res.bookingRef).subscribe({
      next: () => {
        alert(`Guest ${res.customerName} checked out successfully!`);
        this.loadDashboardData();
        if (this.selectedReservation) this.selectedReservation.status = 'CHECKED_OUT';
      },
      error: (err) => alert(err.error?.message || 'Check-out failed')
    });
  }

  markNoShow(res: any): void {
    if (!res?.bookingRef) return;
    if (confirm(`Mark reservation ${res.bookingRef} as NO-SHOW? Any reserved dates will be returned to inventory.`)) {
      this.bookingService.markNoShow(res.bookingRef).subscribe({
        next: () => {
          alert('Reservation marked as NO-SHOW. Inventory released.');
          this.loadDashboardData();
          if (this.selectedReservation) this.selectedReservation.status = 'NO_SHOW';
        },
        error: (err) => alert(err.error?.message || 'Marking no-show failed')
      });
    }
  }

  // ==================== FORMATTERS & UTILITIES ====================
  formatCurrency(val: number): string {
    if (val === undefined || val === null) return '₹0';
    const num = Number(val);
    if (num < 0) {
      return '-₹' + Math.abs(num).toLocaleString('en-IN');
    }
    return '₹' + num.toLocaleString('en-IN');
  }

  formatDateIso(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  formatDateShort(dateStr: string): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length < 3) return dateStr;
    const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = monthNames[parseInt(parts[1], 10) - 1];
    const day = parseInt(parts[2], 10);
    return `${month} ${day}`;
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return `${monthNames[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
    } catch {
      return dateStr;
    }
  }

  extractRoomTypeFromBooking(res: any): string {
    if (!res) return 'Standard Room';
    if (res.detailsJson) {
      try {
        const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
        if (parsed.roomTypeName) return parsed.roomTypeName;
        if (parsed.roomType) return parsed.roomType;
        if (parsed.category) return parsed.category;
      } catch {
        if (typeof res.detailsJson === 'string' && res.detailsJson.trim().length > 0 && !res.detailsJson.startsWith('{')) {
          return res.detailsJson;
        }
      }
    }
    if (res.serviceTitle && res.serviceTitle.includes('-')) {
      const parts = res.serviceTitle.split('-');
      if (parts.length > 1) return parts[1].trim();
    }
    return 'Deluxe Room';
  }

  private extractRoomCountFromBooking(res: any): number {
    if (!res?.detailsJson) return 1;
    try {
      const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
      return parsed.roomCount || parsed.roomsBooked || parsed.count || 1;
    } catch {
      return 1;
    }
  }

  private extractDateFromBooking(res: any, type: 'in' | 'out'): string | null {
    if (!res?.detailsJson) return null;
    try {
      const parsed = typeof res.detailsJson === 'string' ? JSON.parse(res.detailsJson) : res.detailsJson;
      if (type === 'in') return parsed.checkInDate || parsed.checkIn || null;
      if (type === 'out') return parsed.checkOutDate || parsed.checkOut || null;
    } catch {
      return null;
    }
    return null;
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'CONFIRMED': return 'badge-status-confirmed';
      case 'PENDING_PAYMENT':
      case 'INITIATED': return 'badge-status-pending';
      case 'CHECKED_IN': return 'badge-status-checkedin';
      case 'CHECKED_OUT': return 'badge-status-checkedout';
      case 'CANCELLED': return 'badge-status-cancelled';
      case 'NO_SHOW': return 'badge-status-noshow';
      default: return 'badge-status-checkedout';
    }
  }

  getStatusDisplay(status: string): string {
    switch (status) {
      case 'CONFIRMED': return 'Confirmed';
      case 'PENDING_PAYMENT': return 'Pending Payment';
      case 'CHECKED_IN': return 'Checked In';
      case 'CHECKED_OUT': return 'Checked Out';
      case 'CANCELLED': return 'Cancelled';
      case 'NO_SHOW': return 'No Show';
      default: return status || 'Unknown';
    }
  }

  // ==================== PROPERTY REGISTRATION WIZARD METHODS (WP-05 STEP 1) ====================

  startPropertyRegistration(draft?: any): void {
    this.propertyViewMode = 'wizard';
    this.currentWizardStep = 1;
    this.step1Submitted = false;
    this.step1Submitting = false;
    this.step1ErrorMessage = null;
    this.step1SuccessMessage = null;
    this.isStep1Dirty = false;
    this.isStep2Dirty = false;
    this.step2Submitted = false;
    this.step2Submitting = false;
    this.step2ErrorMessage = null;
    this.imageUploadErrors = ['', '', '', ''];
    this.imageUploading = [false, false, false, false];
    this.editingFromReview = false;
    this.accuracyConfirmed = false;
    this.step5Submitted = false;
    this.step5Submitting = false;
    this.step5ErrorMessage = null;
    this.registrationSuccess = false;
    this.activatedPropertyDetails = null;
    this.showTermsModal = false;
    this.reviewSectionsExpanded = {
      basic: true,
      address: false,
      propertyInfo: false,
      facilities: false
    };

    if (draft) {
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
      };
      this.step2Data = {
        addressLine1: draft.addressLine1 || '',
        addressLine2: draft.addressLine2 || '',
        country: draft.country || 'India',
        state: draft.state || '',
        city: draft.city || '',
        postalCode: draft.postalCode || '',
        landmark: draft.landmark || ''
      };
      this.updateAvailableStates();

      this.step3Data = {
        propertyContactNumber: draft.propertyContactNumber || '',
        alternateContactNumber: draft.alternateContactNumber || '',
        propertyEmail: draft.propertyEmail || '',
        websiteUrl: draft.websiteUrl || '',
        registeredBusinessName: draft.registeredBusinessName || '',
        businessRegistrationType: draft.businessRegistrationType || 'Private Limited Company',
        otherBusinessRegistrationType: draft.otherBusinessRegistrationType || '',
        businessRegistrationNumber: draft.businessRegistrationNumber || '',
        registeredBusinessAddress: draft.registeredBusinessAddress || '',
        isGstRegistered: draft.isGstRegistered !== undefined ? !!draft.isGstRegistered : false,
        gstin: draft.gstin || '',
        gstLegalName: draft.gstLegalName || '',
        gstState: draft.gstState || '',
        propertyManagerName: draft.propertyManagerName || '',
        propertyManagerDesignation: draft.propertyManagerDesignation || 'General Manager',
        propertyManagerContactNumber: draft.propertyManagerContactNumber || '',
        propertyManagerEmail: draft.propertyManagerEmail || '',
        checkInTime: draft.checkInTime || '14:00',
        checkOutTime: draft.checkOutTime || '11:00',
        receptionAvailability: (draft.receptionAvailability && draft.receptionAvailability.toUpperCase().includes('LIMITED')) ? 'Limited Operating Hours' : '24-hour Reception',
        receptionOpeningTime: draft.receptionOpeningTime || '',
        receptionClosingTime: draft.receptionClosingTime || ''
      };


      this.step4Data = {
        predefinedAmenities: draft.predefinedAmenities && Array.isArray(draft.predefinedAmenities) ? [...draft.predefinedAmenities] : [],
        customAmenities: draft.customAmenities && Array.isArray(draft.customAmenities) ? [...draft.customAmenities] : [],
        childrenPolicy: draft.childrenPolicy || 'Children Allowed',
        petPolicy: draft.petPolicy || 'Pets Not Allowed',
        smokingPolicy: draft.smokingPolicy || 'Smoking Prohibited',
        extraBedPolicy: draft.extraBedPolicy || 'Extra Bed Available',
        acceptedIdentificationTypes: draft.acceptedIdentificationTypes && Array.isArray(draft.acceptedIdentificationTypes) && draft.acceptedIdentificationTypes.length
          ? [...draft.acceptedIdentificationTypes]
          : ['Passport', 'Aadhaar Card'],
        customIdentificationType: draft.customIdentificationType || '',
        additionalRules: draft.additionalRules || ''
      };

      if (draft.registrationStep && draft.registrationStep >= 5) {
        this.currentWizardStep = 5;
      } else if (draft.registrationStep && draft.registrationStep >= 4) {
        this.currentWizardStep = 4;
      } else if (draft.registrationStep && draft.registrationStep >= 3) {
        this.currentWizardStep = 3;

      } else if (draft.registrationStep && draft.registrationStep >= 2) {
        this.currentWizardStep = 2;
      } else {
        this.currentWizardStep = 1;
      }
      this.wizardImages = [null, null, null, null];
      if (draft.images && Array.isArray(draft.images)) {
        draft.images.forEach((img: any, idx: number) => {
          if (idx < 4) {
            this.wizardImages[idx] = {
              id: img.id,
              imageUrl: img.imageUrl,
              originalFileName: img.originalFileName,
              isCover: img.isCover !== undefined ? img.isCover : (img.cover !== undefined ? img.cover : (idx === 0)),
              cover: img.isCover !== undefined ? img.isCover : (img.cover !== undefined ? img.cover : (idx === 0)),
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
      this.step2Data = {
        addressLine1: '',
        addressLine2: '',
        country: 'India',
        state: '',
        city: '',
        postalCode: '',
        landmark: ''
      };
      this.updateAvailableStates();

      this.step3Data = {
        propertyContactNumber: '',
        alternateContactNumber: '',
        propertyEmail: '',
        websiteUrl: '',
        registeredBusinessName: '',
        businessRegistrationType: 'Private Limited Company',
        otherBusinessRegistrationType: '',
        businessRegistrationNumber: '',
        registeredBusinessAddress: '',
        isGstRegistered: false,
        gstin: '',
        gstLegalName: '',
        gstState: '',
        propertyManagerName: '',
        propertyManagerDesignation: 'General Manager',
        propertyManagerContactNumber: '',
        propertyManagerEmail: '',
        checkInTime: '14:00',
        checkOutTime: '11:00',
        receptionAvailability: '24-hour Reception',
        receptionOpeningTime: '',
        receptionClosingTime: ''
      };
      this.expandedStep3Section = 'contact';
      this.isStep3Dirty = false;
      this.step3Submitted = false;
      this.step3Submitting = false;
      this.step3ErrorMessage = null;
      this.currentWizardStep = 1;
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
    this.step1Data.description = val.length > 300 ? val.substring(0, 300) : val;
    this.isStep1Dirty = true;
    this.step1ErrorMessage = null;
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
    const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/pjpeg', 'image/png', 'image/webp'];
    const fileName = (file.name || '').toLowerCase();
    const hasAllowedExt = fileName.endsWith('.jpg') || fileName.endsWith('.jpeg') || fileName.endsWith('.png') || fileName.endsWith('.webp');
    const isMimeOk = file.type && allowedMimeTypes.includes(file.type.toLowerCase());

    if (!isMimeOk && !hasAllowedExt) {
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
        isCover: !!img!.isCover,
        cover: !!img!.isCover,
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
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 2;
        }
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
    if (this.isStep1Dirty || this.isStep2Dirty || this.isStep3Dirty) {
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


  onCountryChange(): void {
    this.isStep2Dirty = true;
    this.step2ErrorMessage = null;
    this.updateAvailableStates();
    if (this.availableStates.length > 0 && !this.availableStates.includes(this.step2Data.state)) {
      this.step2Data.state = '';
    }
  }

  updateAvailableStates(): void {
    this.availableStates = this.countryStatesMap[this.step2Data.country] || [];
  }

  onStep2FieldChange(): void {
    this.isStep2Dirty = true;
    this.step2ErrorMessage = null;
  }

  isAddressLine1Valid(): boolean {
    return !!this.step2Data.addressLine1 && this.step2Data.addressLine1.trim().length > 0 && this.step2Data.addressLine1.trim().length <= 255;
  }

  isCountryValid(): boolean {
    return !!this.step2Data.country && this.step2Data.country.trim().length > 0;
  }

  isStateValid(): boolean {
    if (!this.step2Data.state || !this.step2Data.state.trim()) return false;
    if (this.availableStates.length > 0) {
      return this.availableStates.includes(this.step2Data.state.trim());
    }
    return this.step2Data.state.trim().length <= 100;
  }

  isCityValid(): boolean {
    return !!this.step2Data.city && this.step2Data.city.trim().length > 0 && this.step2Data.city.trim().length <= 255;
  }

  isPostalCodeValid(): boolean {
    if (!this.step2Data.postalCode || !this.step2Data.postalCode.trim()) return false;
    const code = this.step2Data.postalCode.trim();
    if (this.step2Data.country === 'India') {
      return /^[1-9][0-9]{5}$/.test(code);
    }
    return /^[a-zA-Z0-9\s-]{3,10}$/.test(code);
  }

  isStep2FormValid(): boolean {
    return this.isAddressLine1Valid() &&
           this.isCountryValid() &&
           this.isStateValid() &&
           this.isCityValid() &&
           this.isPostalCodeValid();
  }

  proceedToStep3(): void {
    this.step2Submitted = true;
    this.step2ErrorMessage = null;

    if (!this.isStep2FormValid()) {
      if (!this.isPostalCodeValid() && this.step2Data.country === 'India' && !!this.step2Data.postalCode) {
        this.step2ErrorMessage = 'Please enter a valid 6-digit PIN code for India (e.g., 520010).';
      } else {
        this.step2ErrorMessage = 'Please complete all required fields correctly.';
      }
      return;
    }

    if (!this.step1Data.id) {
      this.step2ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step2Submitting = true;
    const payload: HotelStep2Request = {
      draftId: this.step1Data.id,
      addressLine1: this.step2Data.addressLine1.trim(),
      addressLine2: this.step2Data.addressLine2 ? this.step2Data.addressLine2.trim() : null,
      country: this.step2Data.country.trim(),
      state: this.step2Data.state.trim(),
      city: this.step2Data.city.trim(),
      postalCode: this.step2Data.postalCode.trim(),
      landmark: this.step2Data.landmark ? this.step2Data.landmark.trim() : null
    };

    this.hotelService.saveStep2Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step2Submitting = false;
        this.isStep2Dirty = false;
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 3;
        }
      },
      error: (err) => {
        this.step2Submitting = false;
        console.error('Save Step 2 draft error:', err);
        this.step2ErrorMessage = err.error?.message || 'Failed to save location & address details. Please check your entries and try again.';
      }
    });
  }

  backToStep2(): void {
    this.currentWizardStep = 2;
  }

  // =========================================================================
  // WP-05 STEP 3: PROPERTY INFORMATION METHODS
  // =========================================================================

  toggleStep3Section(section: string): void {
    if (this.expandedStep3Section === section) {
      this.expandedStep3Section = ''; // collapse currently expanded
    } else {
      this.expandedStep3Section = section; // expand chosen subsection
    }
  }

  isStep3SectionExpanded(section: string): boolean {
    return this.expandedStep3Section === section;
  }

  onStep3FieldChange(): void {
    this.isStep3Dirty = true;
    this.step3ErrorMessage = null;
  }

  onGstToggle(registered: boolean): void {
    this.step3Data.isGstRegistered = registered;
    this.isStep3Dirty = true;
    this.step3ErrorMessage = null;
    if (!registered) {
      this.step3Data.gstin = '';
      this.step3Data.gstLegalName = '';
      this.step3Data.gstState = '';
    }
  }

  // --- Subsection 1: Property Contact Details Validation ---

  isPropertyContactNumberValid(): boolean {
    if (!this.step3Data.propertyContactNumber || !this.step3Data.propertyContactNumber.trim()) return false;
    const phone = this.step3Data.propertyContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isAlternateContactNumberValid(): boolean {
    if (!this.step3Data.alternateContactNumber || !this.step3Data.alternateContactNumber.trim()) return true;
    const phone = this.step3Data.alternateContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isPropertyEmailValid(): boolean {
    if (!this.step3Data.propertyEmail || !this.step3Data.propertyEmail.trim()) return false;
    const email = this.step3Data.propertyEmail.trim();
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email);
  }

  isWebsiteUrlValid(): boolean {
    if (!this.step3Data.websiteUrl || !this.step3Data.websiteUrl.trim()) return true;
    const url = this.step3Data.websiteUrl.trim().toLowerCase();
    return url.startsWith('http://') || url.startsWith('https://');
  }

  isContactSectionValid(): boolean {
    return this.isPropertyContactNumberValid() &&
           this.isAlternateContactNumberValid() &&
           this.isPropertyEmailValid() &&
           this.isWebsiteUrlValid();
  }

  // --- Subsection 2: Business Registration Details Validation ---

  isRegisteredBusinessNameValid(): boolean {
    return !!this.step3Data.registeredBusinessName &&
           this.step3Data.registeredBusinessName.trim().length > 0 &&
           this.step3Data.registeredBusinessName.trim().length <= 255;
  }

  isBusinessRegistrationTypeValid(): boolean {
    return !!this.step3Data.businessRegistrationType && this.step3Data.businessRegistrationType.trim().length > 0;
  }

  isOtherBusinessRegistrationTypeValid(): boolean {
    if (this.step3Data.businessRegistrationType === 'Other') {
      return !!this.step3Data.otherBusinessRegistrationType && this.step3Data.otherBusinessRegistrationType.trim().length > 0;
    }
    return true;
  }

  isBusinessRegistrationNumberValid(): boolean {
    return !!this.step3Data.businessRegistrationNumber &&
           this.step3Data.businessRegistrationNumber.trim().length > 0 &&
           this.step3Data.businessRegistrationNumber.trim().length <= 100;
  }

  isBusinessSectionValid(): boolean {
    return this.isRegisteredBusinessNameValid() &&
           this.isBusinessRegistrationTypeValid() &&
           this.isOtherBusinessRegistrationTypeValid() &&
           this.isBusinessRegistrationNumberValid();
  }

  // --- Subsection 3: GST Details Validation ---

  isGstinValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    if (!this.step3Data.gstin || !this.step3Data.gstin.trim()) return false;
    const gstin = this.step3Data.gstin.trim().toUpperCase();
    return /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/.test(gstin);
  }

  isGstLegalNameValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return !!this.step3Data.gstLegalName && this.step3Data.gstLegalName.trim().length > 0;
  }

  isGstStateValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return !!this.step3Data.gstState && this.step3Data.gstState.trim().length > 0;
  }

  isGstSectionValid(): boolean {
    if (!this.step3Data.isGstRegistered) return true;
    return this.isGstinValid() && this.isGstLegalNameValid() && this.isGstStateValid();
  }

  // --- Subsection 4: Property Manager Details Validation ---

  isPropertyManagerNameValid(): boolean {
    return !!this.step3Data.propertyManagerName &&
           this.step3Data.propertyManagerName.trim().length > 0 &&
           this.step3Data.propertyManagerName.trim().length <= 255;
  }

  isPropertyManagerDesignationValid(): boolean {
    return !!this.step3Data.propertyManagerDesignation && this.step3Data.propertyManagerDesignation.trim().length > 0;
  }

  isPropertyManagerContactNumberValid(): boolean {
    if (!this.step3Data.propertyManagerContactNumber || !this.step3Data.propertyManagerContactNumber.trim()) return false;
    const phone = this.step3Data.propertyManagerContactNumber.trim();
    return /^[+]?[0-9\s\-()]{7,20}$/.test(phone);
  }

  isPropertyManagerEmailValid(): boolean {
    if (!this.step3Data.propertyManagerEmail || !this.step3Data.propertyManagerEmail.trim()) return false;
    const email = this.step3Data.propertyManagerEmail.trim();
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email);
  }

  isManagerSectionValid(): boolean {
    return this.isPropertyManagerNameValid() &&
           this.isPropertyManagerDesignationValid() &&
           this.isPropertyManagerContactNumberValid() &&
           this.isPropertyManagerEmailValid();
  }

  // --- Subsection 5: Operating Hours Validation ---

  isCheckInTimeValid(): boolean {
    return !!this.step3Data.checkInTime && this.step3Data.checkInTime.trim().length > 0;
  }

  isCheckOutTimeValid(): boolean {
    return !!this.step3Data.checkOutTime && this.step3Data.checkOutTime.trim().length > 0;
  }

  isOperatingHoursSectionValid(): boolean {
    if (!this.isCheckInTimeValid() || !this.isCheckOutTimeValid()) return false;
    if (this.step3Data.receptionAvailability === 'Limited Operating Hours') {
      return !!this.step3Data.receptionOpeningTime && this.step3Data.receptionOpeningTime.trim().length > 0 &&
             !!this.step3Data.receptionClosingTime && this.step3Data.receptionClosingTime.trim().length > 0;
    }
    return true;
  }

  isStep3FormValid(): boolean {
    return this.isContactSectionValid() &&
           this.isBusinessSectionValid() &&
           this.isGstSectionValid() &&
           this.isManagerSectionValid() &&
           this.isOperatingHoursSectionValid();
  }

  proceedToStep4(): void {
    this.step3Submitted = true;
    this.step3ErrorMessage = null;

    if (!this.isStep3FormValid()) {
      // Auto-expand the first invalid section and display clear error
      if (!this.isContactSectionValid()) {
        this.expandedStep3Section = 'contact';
        this.step3ErrorMessage = 'Please complete all required fields in Property Contact Details correctly.';
      } else if (!this.isBusinessSectionValid()) {
        this.expandedStep3Section = 'business';
        this.step3ErrorMessage = 'Please complete all required fields in Business Registration Details correctly.';
      } else if (!this.isGstSectionValid()) {
        this.expandedStep3Section = 'gst';
        this.step3ErrorMessage = 'Please provide a valid 15-character GSTIN, legal name, and state for GST registration.';
      } else if (!this.isManagerSectionValid()) {
        this.expandedStep3Section = 'manager';
        this.step3ErrorMessage = 'Please complete all required fields in Property Manager Details correctly.';
      } else if (!this.isOperatingHoursSectionValid()) {
        this.expandedStep3Section = 'operating';
        this.step3ErrorMessage = 'Please provide valid check-in, check-out and reception operating hours.';
      }
      return;
    }

    if (!this.step1Data.id) {
      this.step3ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step3Submitting = true;
    const payload: HotelStep3Request = {
      draftId: this.step1Data.id,
      propertyContactNumber: this.step3Data.propertyContactNumber.trim(),
      alternateContactNumber: this.step3Data.alternateContactNumber ? this.step3Data.alternateContactNumber.trim() : null,
      propertyEmail: this.step3Data.propertyEmail.trim(),
      websiteUrl: this.step3Data.websiteUrl ? this.step3Data.websiteUrl.trim() : null,
      registeredBusinessName: this.step3Data.registeredBusinessName.trim(),
      businessRegistrationType: this.step3Data.businessRegistrationType.trim(),
      otherBusinessRegistrationType: this.step3Data.businessRegistrationType === 'Other' && this.step3Data.otherBusinessRegistrationType ? this.step3Data.otherBusinessRegistrationType.trim() : null,
      businessRegistrationNumber: this.step3Data.businessRegistrationNumber.trim(),
      registeredBusinessAddress: this.step3Data.registeredBusinessAddress ? this.step3Data.registeredBusinessAddress.trim() : null,
      isGstRegistered: !!this.step3Data.isGstRegistered,
      gstin: this.step3Data.isGstRegistered && this.step3Data.gstin ? this.step3Data.gstin.trim().toUpperCase() : null,
      gstLegalName: this.step3Data.isGstRegistered && this.step3Data.gstLegalName ? this.step3Data.gstLegalName.trim() : null,
      gstState: this.step3Data.isGstRegistered && this.step3Data.gstState ? this.step3Data.gstState.trim() : null,
      propertyManagerName: this.step3Data.propertyManagerName.trim(),
      propertyManagerDesignation: this.step3Data.propertyManagerDesignation.trim(),
      propertyManagerContactNumber: this.step3Data.propertyManagerContactNumber.trim(),
      propertyManagerEmail: this.step3Data.propertyManagerEmail.trim(),
      checkInTime: this.step3Data.checkInTime.trim(),
      checkOutTime: this.step3Data.checkOutTime.trim(),
      receptionAvailability: this.step3Data.receptionAvailability.trim(),
      receptionOpeningTime: this.step3Data.receptionAvailability === 'Limited Operating Hours' && this.step3Data.receptionOpeningTime ? this.step3Data.receptionOpeningTime.trim() : null,
      receptionClosingTime: this.step3Data.receptionAvailability === 'Limited Operating Hours' && this.step3Data.receptionClosingTime ? this.step3Data.receptionClosingTime.trim() : null
    };

    this.hotelService.saveStep3Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step3Submitting = false;
        this.isStep3Dirty = false;
        if (this.editingFromReview) {
          this.editingFromReview = false;
          this.currentWizardStep = 5;
        } else {
          this.currentWizardStep = 4;
        }
      },
      error: (err) => {
        this.step3Submitting = false;
        console.error('Save Step 3 draft error:', err);
        this.step3ErrorMessage = err.error?.message || 'Failed to save property information. Please check your entries and try again.';
      }
    });
  }

  backToStep3(): void {
    this.currentWizardStep = 3;
  }

  // ==================== STEP 4 METHODS ====================
  toggleStep4Section(section: 'amenities' | 'policies' | 'rules'): void {
    if (this.expandedStep4Section === section) {
      this.expandedStep4Section = null;
    } else {
      this.expandedStep4Section = section;
    }
  }

  isStep4SectionExpanded(section: 'amenities' | 'policies' | 'rules'): boolean {
    return this.expandedStep4Section === section;
  }

  togglePredefinedAmenity(name: string): void {
    const idx = this.step4Data.predefinedAmenities.indexOf(name);
    if (idx >= 0) {
      this.step4Data.predefinedAmenities.splice(idx, 1);
    } else {
      this.step4Data.predefinedAmenities.push(name);
    }
    this.isStep4Dirty = true;
  }

  isPredefinedAmenitySelected(name: string): boolean {
    return this.step4Data.predefinedAmenities.includes(name);
  }

  openAddCustomAmenity(): void {
    this.showCustomAmenityInput = true;
    this.newCustomAmenityName = '';
    this.customAmenityError = '';
  }

  cancelAddCustomAmenity(): void {
    this.showCustomAmenityInput = false;
    this.newCustomAmenityName = '';
    this.customAmenityError = '';
  }

  submitAddCustomAmenity(): void {
    const trimmed = (this.newCustomAmenityName || '').trim();
    if (!trimmed) {
      this.customAmenityError = 'Please enter a valid amenity name.';
      return;
    }
    if (trimmed.length > 50) {
      this.customAmenityError = 'Amenity name must not exceed 50 characters.';
      return;
    }
    const isPredefined = this.predefinedAmenityCategories.some(cat =>
      cat.amenities.some(a => a.toLowerCase() === trimmed.toLowerCase())
    );
    if (isPredefined) {
      this.customAmenityError = 'This facility already exists in the standard categories above.';
      return;
    }
    const isCustomDup = this.step4Data.customAmenities.some(a => a.toLowerCase() === trimmed.toLowerCase());
    if (isCustomDup) {
      this.customAmenityError = 'This custom amenity has already been added.';
      return;
    }

    this.step4Data.customAmenities.push(trimmed);
    this.isStep4Dirty = true;
    this.cancelAddCustomAmenity();
  }

  removeCustomAmenity(name: string): void {
    this.step4Data.customAmenities = this.step4Data.customAmenities.filter(a => a !== name);
    this.isStep4Dirty = true;
  }

  toggleIdType(type: string): void {
    const idx = this.step4Data.acceptedIdentificationTypes.indexOf(type);
    if (idx >= 0) {
      this.step4Data.acceptedIdentificationTypes.splice(idx, 1);
      if (type === 'Other') {
        this.step4Data.customIdentificationType = '';
      }
    } else {
      this.step4Data.acceptedIdentificationTypes.push(type);
    }
    this.isStep4Dirty = true;
  }

  isIdTypeSelected(type: string): boolean {
    return this.step4Data.acceptedIdentificationTypes.includes(type);
  }

  isPoliciesSectionValid(): boolean {
    if (!this.step4Data.childrenPolicy || !this.step4Data.childrenPolicy.trim()) return false;
    if (!this.step4Data.petPolicy || !this.step4Data.petPolicy.trim()) return false;
    if (!this.step4Data.smokingPolicy || !this.step4Data.smokingPolicy.trim()) return false;
    if (!this.step4Data.extraBedPolicy || !this.step4Data.extraBedPolicy.trim()) return false;
    if (!this.step4Data.acceptedIdentificationTypes || this.step4Data.acceptedIdentificationTypes.length === 0) return false;
    if (this.step4Data.acceptedIdentificationTypes.includes('Other')) {
      if (!this.step4Data.customIdentificationType || !this.step4Data.customIdentificationType.trim()) return false;
      if (this.step4Data.customIdentificationType.trim().length > 150) return false;
    }
    return true;
  }

  isRulesSectionValid(): boolean {
    if (this.step4Data.additionalRules && this.step4Data.additionalRules.length > 1000) {
      return false;
    }
    return true;
  }

  isStep4FormValid(): boolean {
    return this.isPoliciesSectionValid() && this.isRulesSectionValid();
  }

  proceedToStep5(): void {
    this.step4Submitted = true;
    this.step4ErrorMessage = null;

    if (!this.isPoliciesSectionValid()) {
      this.expandedStep4Section = 'policies';
      if (!this.step4Data.acceptedIdentificationTypes || this.step4Data.acceptedIdentificationTypes.length === 0) {
        this.step4ErrorMessage = 'Please select at least one accepted identification document.';
      } else if (this.step4Data.acceptedIdentificationTypes.includes('Other') && (!this.step4Data.customIdentificationType || !this.step4Data.customIdentificationType.trim())) {
        this.step4ErrorMessage = 'Please specify the custom identification type when "Other" is selected.';
      } else {
        this.step4ErrorMessage = 'Please select a valid option for all required guest policies.';
      }
      return;
    }

    if (!this.isRulesSectionValid()) {
      this.expandedStep4Section = 'rules';
      this.step4ErrorMessage = 'Additional property rules must not exceed 1000 characters.';
      return;
    }

    if (!this.step1Data.id) {
      this.step4ErrorMessage = 'Draft ID missing. Please return to Step 1 and save basic details first.';
      return;
    }

    this.step4Submitting = true;
    const payload: HotelStep4Request = {
      draftId: this.step1Data.id,
      predefinedAmenities: [...this.step4Data.predefinedAmenities],
      customAmenities: [...this.step4Data.customAmenities],
      childrenPolicy: this.step4Data.childrenPolicy,
      petPolicy: this.step4Data.petPolicy,
      smokingPolicy: this.step4Data.smokingPolicy,
      extraBedPolicy: this.step4Data.extraBedPolicy,
      acceptedIdentificationTypes: [...this.step4Data.acceptedIdentificationTypes],
      customIdentificationType: this.step4Data.acceptedIdentificationTypes.includes('Other') && this.step4Data.customIdentificationType
        ? this.step4Data.customIdentificationType.trim() : null,
      additionalRules: this.step4Data.additionalRules && this.step4Data.additionalRules.trim() ? this.step4Data.additionalRules.trim() : null
    };

    this.hotelService.saveStep4Draft(this.step1Data.id, payload).subscribe({
      next: (res) => {
        this.step4Submitting = false;
        this.isStep4Dirty = false;
        this.editingFromReview = false;
        this.currentWizardStep = 5;
      },
      error: (err) => {
        this.step4Submitting = false;
        console.error('Save Step 4 draft error:', err);
        this.step4ErrorMessage = err.error?.message || 'Failed to save facilities and policies. Please check your entries and try again.';
      }
    });
  }

  backToStep4(): void {
    this.currentWizardStep = 4;
  }


  // =========================================================================
  // WP-05 STEP 5: REVIEW & SUBMIT METHODS
  // =========================================================================

  toggleReviewSection(section: 'basic' | 'address' | 'propertyInfo' | 'facilities', event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.reviewSectionsExpanded[section] = !this.reviewSectionsExpanded[section];
  }

  isReviewSectionExpanded(section: 'basic' | 'address' | 'propertyInfo' | 'facilities'): boolean {
    return !!this.reviewSectionsExpanded[section];
  }

  editReviewStep(step: number, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.editingFromReview = true;
    this.currentWizardStep = step;
  }

  saveAndReturnToReview(step: number): void {
    this.editingFromReview = true;
    if (step === 1) {
      this.proceedToStep2();
    } else if (step === 2) {
      this.proceedToStep3();
    } else if (step === 3) {
      this.proceedToStep4();
    } else if (step === 4) {
      this.proceedToStep5();
    }
  }

  openTermsModal(event?: Event): void {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    this.showTermsModal = true;
  }

  closeTermsModal(): void {
    this.showTermsModal = false;
  }

  submitFinalRegistration(): void {
    this.step5Submitted = true;
    this.step5ErrorMessage = null;

    if (!this.accuracyConfirmed) {
      this.step5ErrorMessage = 'You must confirm that the information provided is accurate and complete and agree to comply with TravelEasy\'s Partner Terms & Conditions.';
      return;
    }

    if (!this.step1Data.id) {
      this.step5ErrorMessage = 'Draft ID missing. Please return to Step 1 and complete required property details.';
      return;
    }

    this.step5Submitting = true;
    const submitPayload: HotelStep5SubmitRequest = {
      draftId: this.step1Data.id,
      confirmationAccepted: true
    };

    this.hotelService.submitPropertyRegistration(this.step1Data.id, submitPayload).subscribe({
      next: (res: HotelStep5Response) => {
        this.step5Submitting = false;
        this.registrationSuccess = true;
        this.activatedPropertyDetails = res;
      },
      error: (err) => {
        this.step5Submitting = false;
        console.error('Final property submission error:', err);
        this.step5ErrorMessage = err.error?.message || 'Failed to submit property registration. Please verify all steps and try again.';
      }
    });
  }

  viewActivatedProperty(): void {
    this.propertyViewMode = 'list';
    this.registrationSuccess = false;
    this.activatedPropertyDetails = null;
    this.loadInitialData();
  }

  getReviewClassification(): string {
    const classification = this.step1Data.propertyClassification;
    if (classification === 'ONE_STAR') return '1 Star';
    if (classification === 'TWO_STAR') return '2 Star';
    if (classification === 'THREE_STAR') return '3 Star';
    if (classification === 'FOUR_STAR') return '4 Star';
    if (classification === 'FIVE_STAR') return '5 Star';
    if (classification === 'FIVE_STAR_DELUXE') return '5 Star Deluxe';
    if (classification === 'OTHER') {
      return this.step1Data.customClassification && this.step1Data.customClassification.trim()
        ? this.step1Data.customClassification.trim()
        : 'Other recognized classification';
    }
    if (classification === 'NOT_CLASSIFIED' || classification === 'NOT_OFFICIALLY_CLASSIFIED') {
      return 'Not officially classified';
    }
    return this.step1Data.propertyClassification || 'Not officially classified';
  }

  getReviewStarRating(): string {
    return this.getReviewClassification();
  }

  getReviewPropertySegment(): string {
    return this.step1Data.propertySegment || 'Standard';
  }

  getReviewCoverImageUrl(): string {
    const cover = this.wizardImages.find(img => img !== null && img.isCover && (img.previewUrl || img.imageUrl));
    if (cover) {
      return cover.previewUrl || cover.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
    }
    const anyImg = this.wizardImages.find(img => img !== null && (img.previewUrl || img.imageUrl));
    if (anyImg) {
      return anyImg.previewUrl || anyImg.imageUrl || 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
    }
    return 'https://images.unsplash.com/photo-1566073771259-6a8506099945';
  }

  getFormattedFullAddress(): string {
    const parts = [];
    if (this.step2Data.addressLine1) parts.push(this.step2Data.addressLine1.trim());
    if (this.step2Data.addressLine2) parts.push(this.step2Data.addressLine2.trim());
    if (this.step2Data.landmark) parts.push(`Near ${this.step2Data.landmark.trim()}`);
    if (this.step2Data.city) parts.push(this.step2Data.city.trim());
    let statePin = '';
    if (this.step2Data.state) statePin += this.step2Data.state.trim();
    if (this.step2Data.postalCode) statePin += ` - ${this.step2Data.postalCode.trim()}`;
    if (statePin) parts.push(statePin);
    if (this.step2Data.country) parts.push(this.step2Data.country.trim());
    return parts.join(', ');
  }

  formatTime12Hour(timeStr?: string): string {
    if (!timeStr) return '--:--';
    const parts = timeStr.split(':');
    if (parts.length < 2) return timeStr;
    let hour = parseInt(parts[0], 10);
    const min = parts[1];
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12;
    if (hour === 0) hour = 12;
    const hourStr = hour < 10 ? `0${hour}` : `${hour}`;
    return `${hourStr}:${min} ${ampm}`;
  }

  getAllSelectedAmenities(): string[] {
    return [...this.step4Data.predefinedAmenities, ...this.step4Data.customAmenities];
  }

  getAmenityIcon(amenity: string): string {
    const lower = amenity.toLowerCase();
    if (lower.includes('wifi') || lower.includes('wi-fi')) return 'fa-solid fa-wifi';
    if (lower.includes('restaurant')) return 'fa-solid fa-utensils';
    if (lower.includes('room service')) return 'fa-solid fa-bell-concierge';
    if (lower.includes('breakfast')) return 'fa-solid fa-mug-saucer';
    if (lower.includes('parking')) return 'fa-solid fa-square-parking';
    if (lower.includes('shuttle')) return 'fa-solid fa-van-shuttle';
    if (lower.includes('pool')) return 'fa-solid fa-person-swimming';
    if (lower.includes('gym')) return 'fa-solid fa-dumbbell';
    if (lower.includes('spa')) return 'fa-solid fa-spa';
    if (lower.includes('cctv')) return 'fa-solid fa-video';
    if (lower.includes('security')) return 'fa-solid fa-shield-halved';
    if (lower.includes('fire')) return 'fa-solid fa-fire-extinguisher';
    if (lower.includes('accessible') || lower.includes('entrance')) return 'fa-solid fa-wheelchair';
    if (lower.includes('elevator') || lower.includes('lift')) return 'fa-solid fa-elevator';
    if (lower.includes('laundry')) return 'fa-solid fa-soap';
    if (lower.includes('reception')) return 'fa-solid fa-bell-concierge';
    if (lower.includes('luggage')) return 'fa-solid fa-suitcase';
    if (lower.includes('business')) return 'fa-solid fa-briefcase';
    return 'fa-solid fa-check';
  }

  getFormattedRulesList(): string[] {
    if (!this.step4Data.additionalRules || !this.step4Data.additionalRules.trim()) {
      return [
        'Outside food and beverages are not allowed.',
        'Maintain quiet hours from 10:00 PM to 6:00 AM.',
        'Valid government-issued ID is required for all guests.',
        'Visitors are allowed only till 9:00 PM.'
      ];
    }
    return this.step4Data.additionalRules
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.length > 0)
      .map(line => line.startsWith('-') || line.startsWith('•') ? line.replace(/^[-•]\s*/, '') : line);
  }

  getFormattedIdentification(): string {
    const types = [...this.step4Data.acceptedIdentificationTypes];
    const otherIdx = types.indexOf('Other');
    if (otherIdx !== -1) {
      types.splice(otherIdx, 1);
      if (this.step4Data.customIdentificationType && this.step4Data.customIdentificationType.trim()) {
        types.push(this.step4Data.customIdentificationType.trim());
      }
    }
    return types.length > 0 ? types.join(', ') : 'None specified';
  }

}
