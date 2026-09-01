export interface NavItem {
  label: string;
  href: string;
  available: boolean;
}

/**
 * Primary navigation. Only Dashboard exists in Module 0 - the rest are
 * listed so the header's structure doesn't need to change as later modules
 * (Incidents, Recovery, Audit Trail, Evaluation) land.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", href: "/", available: true },
  { label: "Payments", href: "/payments", available: true },
  { label: "Simulator", href: "/simulator", available: true },
  { label: "Incidents", href: "/incidents", available: false },
  { label: "Recovery", href: "/recovery", available: false },
  { label: "Audit Trail", href: "/audit", available: false },
  { label: "Evaluation", href: "/evaluation", available: false },
];
