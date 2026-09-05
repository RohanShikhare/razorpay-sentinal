export interface NavItem {
  label: string;
  href: string;
  available: boolean;
}

/**
 * Primary navigation. "Command Center" is the flagship
 * consolidated operations view; "Home" provides
 * the project overview. The focused
 * single-purpose pages (Payments, Simulator, Monitoring, Incidents, Safety Lab, Evaluation) all
 * remain available for drill-down.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: "Command Center", href: "/command-center", available: true },
  { label: "Home", href: "/", available: true },
  { label: "Incidents", href: "/incidents", available: true },
  { label: "Monitoring", href: "/monitoring", available: true },
  { label: "Simulator", href: "/simulator", available: true },
  { label: "Payments", href: "/payments", available: true },
  { label: "Safety Lab", href: "/safety", available: true },
  { label: "Evaluation", href: "/evaluation", available: true },
];

