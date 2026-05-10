import styles from './PostHtmlView.module.css';

const PostHtmlView = ({ html = '' }) => {
  return <div className={styles.content} dangerouslySetInnerHTML={{ __html: html || '<p></p>' }} />;
};

export default PostHtmlView;
