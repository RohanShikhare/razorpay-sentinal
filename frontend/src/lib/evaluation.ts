import { apiFetch } from "./api";

export interface EvaluationScenarioDescriptorDto {
  scenario: string;
  name: string;
  description: string;
  category: string;
  incidentExpected: boolean;
  expectedSafeStrategies: string[];
  expectedPolicyBehavior: string;
  expectedRootCausePattern: string;
}

export interface EvaluationGroundTruthDto {
  scenarioType: string;
  incidentExpected: boolean;
  expectedTargetBank?: string | null;
  expectedTargetPaymentMethod?: string | null;
  expectedTargetMerchantId?: string | null;
  expectedRootCauseKeywords?: string[];
  expectedSafeStrategies?: string[];
  expectedPolicyDecision?: string;
  expectedExecutionAllowed?: boolean;
  scenarioSummary?: string;
}

export interface IncidentEvaluationResultDto {
  evaluationIncidentId: string;
  scenarioType: string;
  groundTruth?: EvaluationGroundTruthDto | null;
  incidentId: number | null;
  detected: boolean;
  detectionLatencyMs: number | null;
  detectedType: string | null;
  detectedSeverity: string | null;
  rootCauseObserved: string | null;
  rootCauseCorrect: boolean;
  diagnosisConfidence: number | null;
  strategyProposed: string | null;
  strategySafe: boolean;
  policyDecision: string | null;
  executionAllowed: boolean;
  executionStatus: string | null;
  revenueAtRisk: number;
  recoverableRevenue: number;
  recoveredRevenue: number;
  naiveRetryRecoveredRevenue: number;
  stoppingReason: string | null;
  auditVerified: boolean;
  status: string;
  notes: string;
}

export interface FinancialMetricsDto {
  totalRevenueAtRisk: number;
  totalRecoverableRevenue: number;
  totalRecoveredRevenue: number;
  totalUnrecoveredRevenue: number;
  financialRecoveryRate: number;
  baselineNoRecoveryAmount: number;
  baselineNaiveRetryAmount: number;
  recoveryUpliftVsNoRecovery: number;
  recoveryUpliftVsNaiveRetry: number;
  recoveryEfficiency: number;
}

export interface DetectionMetricsDto {
  totalScenariosEvaluated: number;
  truePositives: number;
  falsePositives: number;
  falseNegatives: number;
  trueNegatives: number;
  precision: number;
  recall: number;
  f1Score: number;
  averageDetectionLatencyMs: number;
}

export interface DiagnosisMetricsDto {
  totalDiagnosesEvaluated: number;
  correctDiagnoses: number;
  incorrectDiagnoses: number;
  inconclusiveDiagnoses: number;
  diagnosisAccuracy: number;
  averageConfidence: number;
}

export interface StrategyMetricsDto {
  totalStrategiesEvaluated: number;
  safeStrategiesProposed: number;
  unsafeStrategiesProposed: number;
  strategyQualityScore: number;
}

export interface PolicyMetricsEvaluationDto {
  totalPolicyEvaluations: number;
  approvedCount: number;
  approvedWithLimitsCount: number;
  humanReviewCount: number;
  rejectedCount: number;
  executionAllowedCount: number;
  executionBlockedCount: number;
  policyComplianceRate: number;
}

export interface RecoveryMetricsEvaluationDto {
  eligibleTransactions: number;
  attemptedTransactions: number;
  successfulRecoveries: number;
  failedRecoveries: number;
  skippedTransactions: number;
  transactionRecoveryRate: number;
  averageAttemptsPerRecovery: number;
  circuitBreakerTrips: number;
}

export interface SafetyMetricsEvaluationDto {
  safetyScore: number;
  policyBypasses: number;
  unauthorizedExecutions: number;
  financialBoundViolations: number;
  retryLimitViolations: number;
  resolvedIncidentRetries: number;
  duplicateRecoveries: number;
  auditIntegrityFailures: number;
  safetyScenariosPassed: number;
  safetyScenariosFailed: number;
}

export interface EvaluationRunResponse {
  runId: string;
  evaluatorVersion: string;
  startedAt: string;
  completedAt: string;
  status: string;
  seed: number;
  scenarioCount: number;
  completedScenarios: number;
  failedScenarios: number;
  errorScenarios: number;
  overallSentinelScore: number;
  financial: FinancialMetricsDto;
  detection: DetectionMetricsDto;
  diagnosis: DiagnosisMetricsDto;
  strategy: StrategyMetricsDto;
  policy: PolicyMetricsEvaluationDto;
  recovery: RecoveryMetricsEvaluationDto;
  safety: SafetyMetricsEvaluationDto;
  incidents: IncidentEvaluationResultDto[];
  benchmarkDisclaimer: string;
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
 * Fetch the latest completed benchmark evaluation run. Returns null if no run has been recorded yet (404).
 */
export async function getLatestEvaluationRun(): Promise<EvaluationRunResponse | null> {
  try {
    return await apiFetch<EvaluationRunResponse>("/api/v1/evaluation/latest");
  } catch {
    return null;
  }
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
