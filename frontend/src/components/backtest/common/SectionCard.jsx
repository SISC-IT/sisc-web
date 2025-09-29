import { useId, useState } from 'react';
import styles from './SectionCard.module.css';

const SectionCard = ({
  title,
  additionalInfo,
  description,
  actions,
  collapsible = false,
  defaultCollapsed = false,
  children,
  className = '',
  footer,
}) => {
  const titleId = useId();
  const [open, setOpen] = useState(!defaultCollapsed);

  return (
    <section
      aria-labelledby={titleId}
      className={`${styles.section} ${className || ''}`}
    >
      <div className={styles.header}>
        <div>
          <h2 id={titleId} className={styles.title}>
            {title}
            {additionalInfo ? (
              <span className={styles.additionalInfo}>{additionalInfo}</span>
            ) : null}
          </h2>
          {description ? <p className={styles.desc}>{description}</p> : null}
        </div>

        <div className={styles.actions}>
          {actions}
          {collapsible && (
            <button
              type="button"
              className={styles.collapseBtn}
              aria-expanded={open}
              aria-controls={`${titleId}-panel`}
              onClick={() => setOpen((v) => !v)}
            >
              {open ? '접기' : '펼치기'}
            </button>
          )}
        </div>
      </div>

      {open && (
        <div id={`${titleId}-panel`} className={styles.body}>
          {children}
        </div>
      )}

      {footer ? <div className={styles.footer}>{footer}</div> : null}
    </section>
  );
};

export default SectionCard;
