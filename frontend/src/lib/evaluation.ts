import { apiFetch } from "./api";

export interface EvaluationScenarioDescriptorDto {
  scenarioType: string;
  name: string;
  category: string;
  description: string;
  expectedRootCauseKeywords: string[];
  expectedSafeStrategies: string[];
  expectedPolicyDecision: string;
  expectedExecutionStatus: string;
}

export interface IncidentEvaluationResultDto {
  incidentId: number;
  scenarioType: string;
  scenarioName: string;
  detected: boolean;
  timeToDetectMs: number;
  revenueAtRisk: number;
  groundTruthRootCause: string;
  diagnosedRootCause: string;
  diagnosisAccurate: boolean;
  diagnosisConfidence: number;
  investigationAccurate: boolean;
  proposedStrategy: string;
  strategySafe: boolean;
  policyDecision: string;
  policyCompliant: boolean;
  policyViolated: boolean;
  recoveryExecuted: boolean;
  recoveryStatus: string;
  recoveredAmount: number;
  recoverableAmount: number;
  recoveryRatePercent: number;
  recoveryLatencyMs: number;
  baselineNoRecoveryAmount: number;
  baselineNaiveRetryAmount: number;
  auditEventsCount: number;
  auditChainValid: boolean;
  incidentScore: number;
  details: string;
}

export interface FinancialMetricsDto {
  totalRevenueAtRisk: number;
  totalRecoverableRevenue: number;
  sentinelRecoveredRevenue: number;
  baselineNoRecoveryRevenue: number;
  baselineNaiveRetryRevenue: number;
  overallRecoveryRatePercent: number;
  upliftVsNoRecoveryPercent: number;
  upliftVsNaiveRetryPercent: number;
  costBenefitRatio: number;
  netValueAdded: number;
}

export interface DetectionMetricsDto {
  totalScenarios: number;
  detectedCount: number;
  detectionRatePercent: number;
  falsePositiveCount: number;
  falsePositiveRatePercent: number;
  meanTimeToDetectMs: number;
  medianTimeToDetectMs: number;
  minTimeToDetectMs: number;
  maxTimeToDetectMs: number;
}

export interface DiagnosisMetricsDto {
  evaluatedDiagnosesCount: number;
  accurateDiagnosesCount: number;
  rootCauseAccuracyPercent: number;
  meanConfidencePercent: number;
  accurateInvestigationsCount: number;
  investigationAccuracyPercent: number;
}

export interface StrategyMetricsDto {
  proposedStrategiesCount: number;
  safeStrategiesCount: number;
  strategySafetyPrecisionPercent: number;
  strategyAppropriatenessPercent: number;
}

export interface PolicyMetricsEvaluationDto {
  evaluatedDecisionsCount: number;
  compliantDecisionsCount: number;
  policyComplianceRatePercent: number;
  approvedCount: number;
  humanReviewCount: number;
  rejectedCount: number;
  policyBypassesCount: number;
  boundViolationsCount: number;
}

export interface RecoveryMetricsEvaluationDto {
  executedRecoveriesCount: number;
  successfulRecoveriesCount: number;
  recoverySuccessRatePercent: number;
  meanRecoveryLatencyMs: number;
  totalAttemptsExecuted: number;
  successfulAttemptsCount: number;
}

export interface SafetyMetricsEvaluationDto {
  policyCompliancePercent: number;
  safetyScore: number;
  zeroPolicyBypassVerified: boolean;
  zeroBoundViolationVerified: boolean;
  auditChainIntegrityPercent: number;
}

export interface EvaluationMetricsDto {
  financial: FinancialMetricsDto;
  detection: DetectionMetricsDto;
  diagnosis: DiagnosisMetricsDto;
  strategy: StrategyMetricsDto;
  policy: PolicyMetricsEvaluationDto;
  recovery: RecoveryMetricsEvaluationDto;
  safety: SafetyMetricsEvaluationDto;
}

export interface EvaluationRunResponse {
  runId: string;
  evaluatorVersion: string;
  scenarioCount: number;
  randomSeed: number;
  includeBaseline: boolean;
  startedAt: string;
  completedAt: string;
  durationMs: number;
  status: string;
  overallScore: number;
  metrics: EvaluationMetricsDto;
  incidents: IncidentEvaluationResultDto[];
}

export interface EvaluationRunRequest {
  scenarioCount?: number;
  seed?: number;
  includeBaseline?: boolean;
  scenarios?: string[];
}

/**
 * Fetch all available evaluation benchmark scenario descriptors.
 */
export async function getEvaluationScenarios(): Promise<EvaluationScenarioDescriptorDto[]> {
  return apiFetch<EvaluationScenarioDescriptorDto[]>("/api/v1/evaluation/scenarios");
}

/**
 * Run a multi-scenario seeded benchmark evaluation.
 */
export async function runEvaluation(
  request: EvaluationRunRequest
): Promise<EvaluationRunResponse> {
  return apiFetch<EvaluationRunResponse>("/api/v1/evaluation/run", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/**
 * Fetch the latest completed benchmark evaluation run.
 */
export async function getLatestEvaluationRun(): Promise<EvaluationRunResponse> {
  return apiFetch<EvaluationRunResponse>("/api/v1/evaluation/latest");
}

/**
 * Fetch a specific benchmark evaluation run by run ID.
 */
export async function getEvaluationRun(
  runId: string
): Promise<EvaluationRunResponse> {
  return apiFetch<EvaluationRunResponse>(`/api/v1/evaluation/runs/${runId}`);
}

/**
 * Fetch incident-level evaluation breakdown for a specific run.
 */
export async function getEvaluationIncidents(
  runId: string
): Promise<IncidentEvaluationResultDto[]> {
  return apiFetch<IncidentEvaluationResultDto[]>(
    `/api/v1/evaluation/runs/${runId}/incidents`
  );
}
