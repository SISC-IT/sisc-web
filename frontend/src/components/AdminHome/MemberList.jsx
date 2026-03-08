const ROLE_LABELS = {
	SYSTEM_ADMIN: '시스템 관리자',
	PRESIDENT: '회장',
	VICE_PRESIDENT: '부회장',
	TEAM_LEADER: '팀장',
	TEAM_MEMBER: '일반',
	PENDING_MEMBER: '대기회원',
};

const STATUS_LABELS = {
	ACTIVE: '활성',
	INACTIVE: '비활성',
	GRADUATED: '졸업',
	OUT: '탈퇴',
};

const getRoleClassName = (roleLabel, styles) => {
	if (roleLabel === '회장') return styles.rolePresident;
	if (roleLabel === '부회장') return styles.roleManager;
	if (roleLabel === '팀장' || roleLabel === '대기회원') return styles.roleLeader;
	return styles.roleNormal;
};

const getStatusClassName = (statusLabel, styles) => {
	if (statusLabel === '활성') return styles.statusActive;
	if (statusLabel === '비활성' || statusLabel === '졸업') return styles.statusInactive;
	return styles.statusPending;
};

const MemberList = ({ members = [], styles = {} }) => {
	if (members.length === 0) {
		return <div>표시할 회원이 없습니다.</div>;
	}

	return (
		<div className={styles.memberTableWrap}>
			<table className={styles.memberTable}>
				<thead>
					<tr>
						<th>이름</th>
						<th>권한</th>
						<th>상태</th>
					</tr>
				</thead>
				<tbody>
					{members.map((member) => {
						const roleLabel = ROLE_LABELS[member.role] || member.role || '-';
						const statusLabel = STATUS_LABELS[member.status] || member.status || '-';

						return (
							<tr key={member.id}>
								<td>{member.name || '-'}</td>
								<td>
									<span className={`${styles.badge} ${getRoleClassName(roleLabel, styles)}`}>
										{roleLabel}
									</span>
								</td>
								<td>
									<span className={`${styles.badge} ${getStatusClassName(statusLabel, styles)}`}>
										{statusLabel}
									</span>
								</td>
							</tr>
						);
					})}
				</tbody>
			</table>
		</div>
	);
};

export default MemberList;
