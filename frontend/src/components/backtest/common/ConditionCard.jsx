import styles from './ConditionCard.module.css';
import OperandEditor from './OperandEditor';
import { useConditionLogic } from '../../../hooks/useConditionLogic';

const ConditionCard = ({ value, onChange, onRemove }) => {
  const {
    isLoading,
    dict,
    left,
    setLeft,
    right,
    setRight,
    operator,
    setOperator,
    rightIndicatorFilter,
    operatorOptions,
    opLabel,
  } = useConditionLogic(value, onChange);

  if (isLoading) {
    return <div className={styles.card}>Loading...</div>;
  }

  return (
    <div className={styles.card}>
      <div className={styles.cardInner}>
        <OperandEditor
          title="좌항"
          dict={dict}
          value={left}
          setValue={setLeft}
          indicatorFilter={undefined}
        />

        <div className={styles.operatorBox}>
          <span className={styles.fieldLabel}>연산자</span>
          <select
            className={styles.select}
            value={operator}
            onChange={(e) => setOperator(e.target.value)}
          >
            {operatorOptions.map((code) => (
              <option key={code} value={code}>
                {opLabel(code)}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.rightWithRemove}>
          <OperandEditor
            title="우항"
            dict={dict}
            value={right}
            setValue={setRight}
            indicatorFilter={rightIndicatorFilter} // 우항은 호환성 필터 적용
          />

          <button
            type="button"
            className={styles.removeBtn}
            onClick={onRemove}
            aria-label="delete"
          >
            &times;
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConditionCard;
