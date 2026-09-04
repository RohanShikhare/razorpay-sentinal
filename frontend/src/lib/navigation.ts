export interface NavItem {
  label: string;
  href: string;
  available: boolean;
}

/**
 * Primary navigation. "Command Center" (Module 5) is the flagship
 * consolidated operations view; "Home" (formerly "Dashboard", Module 0) is
 * preserved as the project overview/foundation page. The focused
 * single-purpose pages (Payments, Simulator, Monitoring, Incidents) all
 * remain available for drill-down.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: "Command Center", href: "/dashboard", available: true },
  { label: "Home", href: "/", available: true },
  { label: "Payments", href: "/payments", available: true },
  { label: "Simulator", href: "/simulator", available: true },
  { label: "Monitoring", href: "/monitoring", available: true },
  { label: "Incidents", href: "/incidents", available: true },
  { label: "Safety Lab", href: "/safety", available: true },
  { label: "Recovery", href: "/recovery", available: false },
  { label: "Audit Trail", href: "/audit", available: false },
  { label: "Evaluation", href: "/evaluation", available: false },
];

