import { useCallback, useEffect, useState } from 'react';
import { CalendarDays, Trophy } from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './AdminCompetition.module.css';
import {
  getCurrentCompetition,
  getRankings,
  getTeamRankings,
} from '../../utils/competitionApi';
import {
  createCompetition,
  finalizeCompetition,
} from '../../utils/adminCompetitionApi';
import {
  changeColor,
  ratePercent,
  signedPercent,
  won,
} from '../../utils/competitionFormat';
import { toastConfirm } from '../../utils/toastConfirm';

const STATUS_LABELS = {
  READY: '시작 전',
  ACTIVE: '진행 중',
  ENDED: '종료, 결과 비공개',
  FINALIZED: '결과 공개',
};

const STEPS = ['READY', 'ACTIVE', 'ENDED', 'FINALIZED'];

const AdminCompetition = () => {
  // undefined는 불러오는 중, null은 대회가 한 번도 없음
  const [competition, setCompetition] = useState(undefined);
  const [error, setError] = useState(null);

  const loadCompetition = useCallback(() => {
    getCurrentCompetition()
      .then(setCompetition)
      .catch((err) => {
        if (err.status === 404) setCompetition(null);
        else setError(err);
      });
  }, []);

  useEffect(() => {
    loadCompetition();
  }, [loadCompetition]);

  if (error) return <p className={styles.helpText}>{error.message}</p>;
  if (competition === undefined) {
    return <p className={styles.helpText}>불러오는 중…</p>;
  }

  const status = competition?.status;

  return (
    <div className={styles.container}>
      {competition && <CompetitionCard competition={competition} />}

      {(!competition || status === 'FINALIZED') && (
        <CreateForm onCreated={loadCompetition} />
      )}

      {status === 'ENDED' && (
        <FinalizePanel
          competitionId={competition.competitionId}
          onFinalized={loadCompetition}
        />
      )}

      {competition && status !== 'READY' && (
        <RankingPanels
          key={competition.competitionId}
          competitionId={competition.competitionId}
          isFinal={status === 'ENDED' || status === 'FINALIZED'}
        />
      )}
    </div>
  );
};

const CompetitionCard = ({ competition }) => {
  const { status } = competition;
  const currentStep = STEPS.indexOf(status);

  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <div>
          <h2 className={styles.panelTitle}>{competition.name}</h2>
          <p className={styles.panelSubText}>
            {competition.startDate} ~ {competition.endDate}
          </p>
        </div>
        <span className={`${styles.badge} ${styles[`badge${status}`]}`}>
          {STATUS_LABELS[status]}
        </span>
      </div>

      <ol className={styles.steps}>
        {STEPS.map((step, index) => (
          <li
            key={step}
            className={`${styles.step} ${index <= currentStep ? styles.stepDone : ''}`}
          >
            <span className={styles.stepDot} />
            {STATUS_LABELS[step]}
          </li>
        ))}
      </ol>

      <div className={styles.statsGrid}>
        <Stat
          icon={CalendarDays}
          label="초기 자산"
          value={`${won(competition.initialCash)}원`}
        />
        <Stat label="매매 수수료" value={ratePercent(competition.feeRate)} />
        <Stat
          label="예금 이자 (연)"
          value={ratePercent(competition.depositInterestRate)}
        />
      </div>

      <p className={styles.helpText}>{statusHelpText(status)}</p>
    </section>
  );
};

const statusHelpText = (status) => {
  if (status === 'READY')
    return '시작일 0시가 지나면 자동으로 진행 중으로 바뀝니다.';
  if (status === 'ACTIVE')
    return '주문과 예금 입출금은 종료 전날 21:00까지 받습니다. 종료일 16:00 순위로 최종 등수를 정합니다.';
  if (status === 'ENDED')
    return '최종 등수가 확정됐습니다. 확인 후 결과를 공개해 주세요.';
  return '결과가 참가자에게 공개됐습니다. 다음 대회를 만들 수 있습니다.';
};

const Stat = ({ icon: Icon, label, value }) => (
  <div className={styles.statCard}>
    <div className={styles.statHeader}>
      <span>{label}</span>
      {Icon && <Icon size={14} />}
    </div>
    <strong className={styles.statValue}>{value}</strong>
  </div>
);

const EMPTY_FORM = { name: '', startDate: '', endDate: '' };

const CreateForm = ({ onCreated }) => {
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  const updateField = (field) => (event) =>
    setForm((prev) => ({ ...prev, [field]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      const created = await createCompetition(form);
      toast.success(
        `${created.name}을 만들었습니다. 참가자 ${created.participantCount}명이 등록됐습니다.`
      );
      setForm(EMPTY_FORM);
      onCreated();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className={styles.panel} onSubmit={handleSubmit}>
      <h2 className={styles.panelTitle}>새 대회 만들기</h2>
      <p className={styles.panelSubText}>
        만들면 활성 회원 전체가 참가자로 자동 등록됩니다. 열린 대회는 하나만 둘
        수 있습니다.
      </p>

      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span>대회 이름</span>
          <input
            className={styles.input}
            placeholder="예: 2026 겨울 수익률 대결"
            maxLength={100}
            required
            value={form.name}
            onChange={updateField('name')}
          />
        </label>
        <label className={styles.field}>
          <span>시작일</span>
          <input
            className={styles.input}
            type="date"
            required
            value={form.startDate}
            onChange={updateField('startDate')}
          />
        </label>
        <label className={styles.field}>
          <span>종료일</span>
          <input
            className={styles.input}
            type="date"
            required
            value={form.endDate}
            onChange={updateField('endDate')}
          />
        </label>
      </div>

      <p className={styles.helpText}>
        초기 자산 1,000만원, 수수료 0.01%, 예금 이자 연 3%는 고정입니다.
        시작일은 오늘 이후, 종료일은 시작일 다음 날 이후여야 합니다.
      </p>

      <div className={styles.actions}>
        <button
          type="submit"
          className={styles.primaryButton}
          disabled={submitting}
        >
          {submitting ? '만드는 중…' : '대회 만들기'}
        </button>
      </div>
    </form>
  );
};

const FinalizePanel = ({ competitionId, onFinalized }) => {
  const [submitting, setSubmitting] = useState(false);

  const handleFinalize = async () => {
    const confirmed = await toastConfirm(
      '공개하면 참가자 화면에 최종 순위가 보입니다. 되돌릴 수 없습니다.',
      { title: '결과 공개', confirmText: '공개하기' }
    );
    if (!confirmed) return;

    setSubmitting(true);
    try {
      await finalizeCompetition(competitionId);
      toast.success('결과를 공개했습니다.');
      onFinalized();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className={`${styles.panel} ${styles.panelHighlight}`}>
      <div className={styles.panelHeader}>
        <div>
          <h2 className={styles.panelTitle}>결과 공개</h2>
          <p className={styles.panelSubText}>
            공개하면 참가자 화면에 최종 순위가 보입니다. 되돌릴 수 없습니다.
          </p>
        </div>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={submitting}
          onClick={handleFinalize}
        >
          <Trophy size={16} />
          결과 공개하기
        </button>
      </div>
    </section>
  );
};

const RankingPanels = ({ competitionId, isFinal }) => {
  const [people, setPeople] = useState(null);
  const [teams, setTeams] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getRankings(competitionId), getTeamRankings(competitionId)])
      .then(([peopleRes, teamsRes]) => {
        setPeople(peopleRes);
        setTeams(teamsRes);
      })
      .catch(setError);
  }, [competitionId]);

  if (error) return <p className={styles.helpText}>{error.message}</p>;
  if (!people || !teams) {
    return <p className={styles.helpText}>순위를 불러오는 중…</p>;
  }

  const prefix = isFinal ? '최종 ' : '';
  const basis = people.snapshotDate
    ? `${people.snapshotDate} 종가 기준`
    : '아직 계산된 순위가 없습니다';

  return (
    <div className={styles.twoColumn}>
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>개인 {prefix}순위</h2>
        <p className={styles.panelSubText}>{basis}</p>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>등수</th>
              <th>이름</th>
              <th>팀</th>
              <th className={styles.alignRight}>총자산</th>
              <th className={styles.alignRight}>수익률</th>
            </tr>
          </thead>
          <tbody>
            {people.rankings.map((row) => (
              <tr key={row.userId}>
                <td>{row.rank}</td>
                <td>{row.userName}</td>
                <td>{row.teamName}</td>
                <td className={styles.alignRight}>{won(row.totalValue)}원</td>
                <td
                  className={styles.alignRight}
                  style={{ color: changeColor(row.profitRate) }}
                >
                  {signedPercent(row.profitRate)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>팀 {prefix}순위</h2>
        <p className={styles.panelSubText}>
          팀원 개인 등수의 평균이 낮은 팀이 앞섭니다
        </p>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>등수</th>
              <th>팀</th>
              <th className={styles.alignRight}>평균 등수</th>
              <th className={styles.alignRight}>인원</th>
            </tr>
          </thead>
          <tbody>
            {teams.rankings.map((row) => (
              <tr key={row.teamName}>
                <td>{row.rank}</td>
                <td>{row.teamName}</td>
                <td className={styles.alignRight}>{row.averageRank}</td>
                <td className={styles.alignRight}>{row.memberCount}명</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
};

export default AdminCompetition;
