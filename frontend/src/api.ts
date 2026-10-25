export interface ScheduleItem {
  jobName: string;
  jobGroup: string;
  description: string | null;
  cronExpression: string;
  triggerState: 'NORMAL' | 'PAUSED' | 'COMPLETE' | 'ERROR' | 'BLOCKED' | 'NONE';
  nextFireTime: string | null;
  previousFireTime: string | null;
  jobData: Record<string, unknown>;
}

export interface ScheduleHistoryItem {
  id: number;
  jobName: string;
  jobGroup: string;
  status: 'SUCCESS' | 'FAIL';
  message: string;
  executedAt: string;
}

export interface ScheduleFormPayload {
  jobName: string;
  jobGroup: string;
  cronExpression: string;
  description: string;
  jobData: {
    taskType: string;
    payload: string;
  };
}

export interface ScheduleUpdatePayload {
  cronExpression: string;
  description: string;
  jobData: {
    taskType: string;
    payload: string;
  };
}

const API_BASE = '/api/schedules';

export async function fetchSchedules(): Promise<ScheduleItem[]> {
  const res = await fetch(API_BASE);
  if (!res.ok) {
    throw new Error('스케줄 목록을 불러오지 못했습니다.');
  }
  return res.json();
}

export async function fetchHistories(): Promise<ScheduleHistoryItem[]> {
  const res = await fetch(`${API_BASE}/histories`);
  if (!res.ok) {
    throw new Error('실행 이력을 불러오지 못했습니다.');
  }
  return res.json();
}

export async function createScheduleApi(payload: ScheduleFormPayload): Promise<ScheduleItem> {
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || '스케줄 생성에 실패했습니다.');
  }
  return res.json();
}

export async function updateScheduleApi(
  jobGroup: string,
  jobName: string,
  payload: ScheduleUpdatePayload
): Promise<ScheduleItem> {
  const encodedGroup = encodeURIComponent(jobGroup);
  const encodedName = encodeURIComponent(jobName);
  const res = await fetch(`${API_BASE}/${encodedGroup}/${encodedName}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || '스케줄 수정에 실패했습니다.');
  }
  return res.json();
}

export async function controlScheduleApi(
  jobGroup: string,
  jobName: string,
  action: 'pause' | 'resume' | 'run-now' | 'delete'
): Promise<void> {
  const encodedGroup = encodeURIComponent(jobGroup);
  const encodedName = encodeURIComponent(jobName);
  const url =
    action === 'delete'
      ? `${API_BASE}/${encodedGroup}/${encodedName}`
      : `${API_BASE}/${encodedGroup}/${encodedName}/${action}`;

  const res = await fetch(url, {
    method: action === 'delete' ? 'DELETE' : 'POST',
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error || `작업(${action}) 요청에 실패했습니다.`);
  }
}
