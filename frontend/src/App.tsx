import {
  useActionState,
  useEffect,
  useOptimistic,
  useState,
  useTransition,
  type FocusEvent,
  type FormEvent,
} from 'react';
import {
  controlScheduleApi,
  createScheduleApi,
  fetchHistories,
  fetchSchedules,
  updateScheduleApi,
  type ScheduleHistoryItem,
  type ScheduleItem,
} from './api';

interface FormActionState {
  error?: string;
  message?: string;
}

const CRON_PRESETS = [
  { label: '10초마다', value: '0/10 * * * * ?' },
  { label: '30초마다', value: '0/30 * * * * ?' },
  { label: '1분마다', value: '0 0/1 * * * ?' },
  { label: '매일 09:00', value: '0 0 9 * * ?' },
];

export default function App() {
  const [schedules, setSchedules] = useState<ScheduleItem[]>([]);
  const [histories, setHistories] = useState<ScheduleHistoryItem[]>([]);
  const [editingItem, setEditingItem] = useState<ScheduleItem | null>(null);
  const [cronInput, setCronInput] = useState('0/10 * * * * ?');
  const [controlError, setControlError] = useState<string | null>(null);
  const [isRefreshing, startTransition] = useTransition();

  // React 19 useOptimistic: 일시정지/재개/삭제 시 UI 즉각 반영
  const [optimisticSchedules, applyOptimistic] = useOptimistic(
    schedules,
    (
      current: ScheduleItem[],
      action: { type: 'pause' | 'resume' | 'delete'; key: string }
    ) => {
      if (action.type === 'delete') {
        return current.filter((item) => `${item.jobGroup}.${item.jobName}` !== action.key);
      }
      return current.map((item) =>
        `${item.jobGroup}.${item.jobName}` === action.key
          ? { ...item, triggerState: action.type === 'pause' ? 'PAUSED' : 'NORMAL' }
          : item
      );
    }
  );

  const loadAllData = async () => {
    try {
      const [scheduleList, historyList] = await Promise.all([
        fetchSchedules(),
        fetchHistories(),
      ]);
      setSchedules(scheduleList);
      setHistories(historyList);
    } catch (e) {
      setControlError(e instanceof Error ? e.message : '데이터 조회 실패');
    }
  };

  useEffect(() => {
    loadAllData();
    // 5초마다 실행 이력 및 다음 실행 시간 자동 갱신
    const timer = setInterval(loadAllData, 5000);
    return () => clearInterval(timer);
  }, []);

  // React 19 useActionState: 스케줄 생성 및 수정 Form Action
  const [formState, formAction, isSubmitting] = useActionState(
    async (_prevState: FormActionState, formData: FormData): Promise<FormActionState> => {
      const mode = formData.get('mode') as 'create' | 'edit';
      const payload = {
        jobName: String(formData.get('jobName') ?? '').trim(),
        jobGroup: String(formData.get('jobGroup') ?? 'DEFAULT').trim() || 'DEFAULT',
        cronExpression: String(formData.get('cronExpression') ?? '').trim(),
        description: String(formData.get('description') ?? '').trim(),
        jobData: {
          taskType: String(formData.get('taskType') ?? 'EMAIL_REPORT'),
          payload: String(formData.get('payload') ?? '{}'),
        },
      };

      try {
        if (mode === 'edit') {
          await updateScheduleApi(payload.jobGroup, payload.jobName, {
            cronExpression: payload.cronExpression,
            description: payload.description,
            jobData: payload.jobData,
          });
        } else {
          await createScheduleApi(payload);
        }
        setEditingItem(null);
        await loadAllData();
        return {
          message:
            mode === 'edit'
              ? `[${payload.jobGroup}.${payload.jobName}] 스케줄이 수정되었습니다.`
              : `[${payload.jobGroup}.${payload.jobName}] 스케줄이 등록되었습니다.`,
        };
      } catch (err) {
        return {
          error: err instanceof Error ? err.message : '요청 처리 중 오류가 발생했습니다.',
        };
      }
    },
    {}
  );

  const handleControl = (
    jobGroup: string,
    jobName: string,
    action: 'pause' | 'resume' | 'run-now' | 'delete'
  ) => {
    setControlError(null);
    startTransition(async () => {
      const key = `${jobGroup}.${jobName}`;
      if (action === 'pause' || action === 'resume' || action === 'delete') {
        applyOptimistic({ type: action, key });
      }
      try {
        await controlScheduleApi(jobGroup, jobName, action);
        await loadAllData();
      } catch (err) {
        setControlError(err instanceof Error ? err.message : '제어 요청 실패');
        await loadAllData();
      }
    });
  };

  const startEdit = (item: ScheduleItem) => {
    setEditingItem(item);
    setCronInput(item.cronExpression);
  };

  const cancelEdit = () => {
    setEditingItem(null);
    setCronInput('0/10 * * * * ?');
  };

  // 접근성: :user-invalid 시각 상태와 aria-invalid 동기화
  const syncAriaInvalid = (
    e: FocusEvent<HTMLInputElement> | FormEvent<HTMLInputElement>
  ) => {
    const input = e.currentTarget;
    if (!input.checkValidity()) {
      input.setAttribute('aria-invalid', 'true');
    } else {
      input.removeAttribute('aria-invalid');
    }
  };

  const formatDateTime = (value: string | null) => {
    if (!value) return '-';
    return value.replace('T', ' ').slice(0, 19);
  };

  return (
    <div className="container">
      <header className="header">
        <div>
          <h1>Quartz 동적 스케줄 매니저</h1>
          <p>React 19 + Spring Boot 3 Quartz + H2 Database (JDBC JobStore)</p>
        </div>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={isRefreshing}
          onClick={() => startTransition(() => loadAllData())}
        >
          {isRefreshing ? '갱신 중...' : '새로고침'}
        </button>
      </header>

      <div className="grid-layout">
        {/* 좌측: 스케줄 생성 / 수정 폼 (React 19 <form action={formAction}>) */}
        <section className="card">
          <h2>
            <span>{editingItem ? '스케줄 수정' : '새 스케줄 등록'}</span>
            {editingItem && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={cancelEdit}>
                신규 등록으로 전환
              </button>
            )}
          </h2>

          <form action={formAction} key={editingItem ? `${editingItem.jobGroup}.${editingItem.jobName}` : 'create'}>
            <input type="hidden" name="mode" value={editingItem ? 'edit' : 'create'} />

            <div className="field">
              <label htmlFor="jobName">
                작업 이름 (jobName)
                <span className="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="jobName"
                name="jobName"
                type="text"
                placeholder="예: dailyReportJob"
                defaultValue={editingItem?.jobName ?? ''}
                readOnly={Boolean(editingItem)}
                required
                aria-errormessage="jobName-error"
                onBlur={syncAriaInvalid}
                onInput={syncAriaInvalid}
              />
              <div id="jobName-error" className="error-msg">
                <span aria-hidden="true">❌ </span>작업 이름을 입력해주세요.
              </div>
            </div>

            <div className="field">
              <label htmlFor="jobGroup">작업 그룹 (jobGroup)</label>
              <input
                id="jobGroup"
                name="jobGroup"
                type="text"
                placeholder="기본값: DEFAULT"
                defaultValue={editingItem?.jobGroup ?? 'REPORT'}
                readOnly={Boolean(editingItem)}
              />
            </div>

            <div className="field">
              <label htmlFor="cronExpression">
                Quartz Cron 표현식
                <span className="required-mark" aria-hidden="true">*</span>
              </label>
              <input
                id="cronExpression"
                name="cronExpression"
                type="text"
                value={cronInput}
                onChange={(e) => setCronInput(e.target.value)}
                placeholder="예: 0/10 * * * * ?"
                required
                aria-errormessage="cron-error"
                onBlur={syncAriaInvalid}
                onInput={syncAriaInvalid}
              />
              <div id="cron-error" className="error-msg">
                <span aria-hidden="true">❌ </span>Quartz Cron 표현식은 필수입니다.
              </div>
              <div className="preset-chips">
                {CRON_PRESETS.map((preset) => (
                  <button
                    key={preset.value}
                    type="button"
                    className="chip-btn"
                    onClick={() => setCronInput(preset.value)}
                  >
                    {preset.label} ({preset.value})
                  </button>
                ))}
              </div>
            </div>

            <div className="field">
              <label htmlFor="description">설명</label>
              <input
                id="description"
                name="description"
                type="text"
                placeholder="예: 10초마다 리포트 발송"
                defaultValue={editingItem?.description ?? ''}
              />
            </div>

            <div className="field">
              <label htmlFor="taskType">작업 유형 (jobData.taskType)</label>
              <select
                id="taskType"
                name="taskType"
                defaultValue={String(editingItem?.jobData?.taskType ?? 'EMAIL_REPORT')}
              >
                <option value="EMAIL_REPORT">EMAIL_REPORT (이메일 발송)</option>
                <option value="WEBHOOK_SYNC">WEBHOOK_SYNC (외부 웹훅 호출)</option>
                <option value="DATA_CLEANUP">DATA_CLEANUP (데이터 정리)</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="payload">전달 파라미터 (jobData.payload)</label>
              <input
                id="payload"
                name="payload"
                type="text"
                defaultValue={String(
                  editingItem?.jobData?.payload ?? '{"target":"admin@sub.com"}'
                )}
              />
            </div>

            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting
                ? '처리 중...'
                : editingItem
                ? '스케줄 수정 저장'
                : '스케줄 생성'}
            </button>

            {formState.error && (
              <div className="alert alert-error" role="alert">
                {formState.error}
              </div>
            )}
            {formState.message && (
              <div className="alert alert-success" role="status">
                {formState.message}
              </div>
            )}
          </form>
        </section>

        {/* 우측: 등록된 스케줄 목록 & 실행 이력 */}
        <div style={{ display: 'grid', gap: 24 }}>
          <section className="card">
            <h2>
              <span>등록된 스케줄 ({optimisticSchedules.length})</span>
            </h2>

            {controlError && (
              <div className="alert alert-error" style={{ marginBottom: 12 }}>
                {controlError}
              </div>
            )}

            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>그룹 / 작업명</th>
                    <th>Cron 주기</th>
                    <th>상태</th>
                    <th>다음 실행 예정</th>
                    <th>제어</th>
                  </tr>
                </thead>
                <tbody>
                  {optimisticSchedules.length === 0 ? (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', color: '#64748b', padding: 28 }}>
                        등록된 스케줄이 없습니다. 왼쪽 폼에서 스케줄을 생성해보세요.
                      </td>
                    </tr>
                  ) : (
                    optimisticSchedules.map((item) => (
                      <tr key={`${item.jobGroup}.${item.jobName}`}>
                        <td>
                          <div style={{ fontWeight: 600 }}>{item.jobName}</div>
                          <div style={{ fontSize: '0.78rem', color: '#64748b' }}>
                            [{item.jobGroup}] {item.description || '설명 없음'}
                          </div>
                        </td>
                        <td>
                          <span className="code-pill">{item.cronExpression}</span>
                        </td>
                        <td>
                          <span
                            className={`badge ${
                              item.triggerState === 'PAUSED' ? 'badge-paused' : 'badge-normal'
                            }`}
                          >
                            {item.triggerState}
                          </span>
                        </td>
                        <td>{formatDateTime(item.nextFireTime)}</td>
                        <td>
                          <div className="action-group">
                            {item.triggerState === 'PAUSED' ? (
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() =>
                                  handleControl(item.jobGroup, item.jobName, 'resume')
                                }
                              >
                                재개
                              </button>
                            ) : (
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() =>
                                  handleControl(item.jobGroup, item.jobName, 'pause')
                                }
                              >
                                일시정지
                              </button>
                            )}
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() =>
                                handleControl(item.jobGroup, item.jobName, 'run-now')
                              }
                            >
                              즉시실행
                            </button>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => startEdit(item)}
                            >
                              수정
                            </button>
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              onClick={() =>
                                handleControl(item.jobGroup, item.jobName, 'delete')
                              }
                            >
                              삭제
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="card">
            <h2>
              <span>최근 실행 이력 (H2 DB 기록 - 5초마다 자동 갱신)</span>
            </h2>
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>실행 시각</th>
                    <th>그룹 / 작업명</th>
                    <th>결과</th>
                    <th>메시지</th>
                  </tr>
                </thead>
                <tbody>
                  {histories.length === 0 ? (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', color: '#64748b', padding: 24 }}>
                        아직 실행된 이력이 없습니다.
                      </td>
                    </tr>
                  ) : (
                    histories.slice(0, 15).map((hist) => (
                      <tr key={hist.id}>
                        <td>{formatDateTime(hist.executedAt)}</td>
                        <td>
                          <strong>{hist.jobName}</strong>{' '}
                          <span style={{ color: '#64748b', fontSize: '0.78rem' }}>
                            ({hist.jobGroup})
                          </span>
                        </td>
                        <td>
                          <span
                            className={`badge ${
                              hist.status === 'SUCCESS' ? 'badge-normal' : 'badge-fail'
                            }`}
                          >
                            {hist.status}
                          </span>
                        </td>
                        <td>{hist.message}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
