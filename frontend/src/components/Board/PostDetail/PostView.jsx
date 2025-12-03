import React from 'react';
import styles from './PostDetail.module.css';
import ProfileIcon from '../../assets/board_profile.svg';
import EditIcon from '../../assets/boardPencil.svg';
import DeleteIcon from '../../assets/boardCloseIcon.svg';
import { getTimeAgo } from '../../utils/TimeUtils';
import FileAttachmentList from './FileAttachmentList';

const PostView = ({
  post,
  showMenu,
  setShowMenu,
  onEdit,
  onDelete,
  onDownload,
}) => {
  const authorName =
    post.author || post.user?.name || post.createdBy?.name || '운영진';
  const date = post.createdDate || post.createdAt || post.date;

  return (
    <>
      <div className={styles.titleWrapper}>
        <h1 className={styles.title}>{post.title}</h1>
        <div className={styles.menuContainer}>
          <button
            className={styles.menuButton}
            onClick={() => setShowMenu(!showMenu)}
            aria-label="게시글 메뉴 열기"
          >
            ⋮
          </button>
          {showMenu && (
            <div className={styles.menuDropdown}>
              <button onClick={onEdit}>
                <img src={EditIcon} className={styles.EditIcon} alt="수정" />
                수정하기
              </button>
              <button onClick={onDelete}>
                <img
                  src={DeleteIcon}
                  className={styles.DeleteIcon}
                  alt="삭제"
                />
                삭제하기
              </button>
            </div>
          )}
        </div>
      </div>

      <div className={styles.divider} />

      <div className={styles.meta}>
        <img src={ProfileIcon} className={styles.profileIcon} alt="프로필" />
        <div className={styles.metaInfo}>
          <p className={styles.author}>{authorName}</p>
          <p className={styles.date}>{getTimeAgo(date)}</p>
        </div>
      </div>

      <div className={styles.content}>{post.content}</div>

      <div className={styles.attachments}>
        {post.attachments && post.attachments.length > 0 && (
          <>
            <h3 className={styles.attachmentTitle}>
              첨부 파일 ({post.attachments.length})
            </h3>
            <FileAttachmentList
              files={post.attachments}
              onDownload={onDownload}
            />
          </>
        )}
      </div>
    </>
  );
};

export default PostView;
