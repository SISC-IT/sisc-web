import { useCallback } from 'react';
import styles from './RulesCard.module.css';
import ConditionCard from './ConditionCard';

const makeDefaultRule = () => ({
  id: crypto.randomUUID(),
  left: undefined,
  right: undefined,
  operator: undefined,
});

export default function RulesCard({ rules, setRules }) {
  const addRule = useCallback(
    () => setRules((prev) => [...prev, makeDefaultRule()]),
    [setRules]
  );

  // patch로 실제 변화가 없으면 원 배열을 그대로 반환해 불필요한 렌더를 막습니다.
  const updateRule = useCallback(
    (id, patch) =>
      setRules((prev) => {
        let changed = false;
        const next = prev.map((r) => {
          if (r.id !== id) return r;
          const merged = { ...r, ...patch };
          // 얕은 비교로 변경 여부 판정
          for (const k in merged) {
            if (merged[k] !== r[k]) {
              changed = true;
              break;
            }
          }
          return changed ? merged : r;
        });
        return changed ? next : prev;
      }),
    [setRules]
  );

  // rule.id별 onChange 함수를 안정적으로 유지
  const makeOnChange = useCallback(
    (id) => (patch) => updateRule(id, patch),
    [updateRule]
  );

  const removeRule = useCallback(
    (id) => setRules((prev) => prev.filter((r) => r.id !== id)),
    [setRules]
  );

  return (
    <div className={styles.wrapper}>
      <div className={styles.headerRow}>
        <div className={styles.headerTitle}>조건</div>
        <button type="button" className={styles.addBtn} onClick={addRule}>
          조건 추가 +
        </button>
      </div>

      {rules.length === 0 ? (
        <div className={styles.empty}>아직 조건이 없습니다.</div>
      ) : (
        rules.map((rule) => (
          <ConditionCard
            key={rule.id}
            value={rule}
            onChange={makeOnChange(rule.id)}
            onRemove={() => removeRule(rule.id)}
          />
        ))
      )}
    </div>
  );
}
