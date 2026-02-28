const MemberList = ({ members = [] }) => {
	if (members.length === 0) {
		return <div>표시할 회원이 없습니다.</div>;
	}

	return (
		<div>
			<table style={{ width: '100%', borderCollapse: 'collapse' }}>
				<thead>
					<tr>
						<th style={{ textAlign: 'left', padding: '8px' }}>이름</th>
						<th style={{ textAlign: 'left', padding: '8px' }}>권한</th>
						<th style={{ textAlign: 'left', padding: '8px' }}>상태</th>
					</tr>
				</thead>
				<tbody>
					{members.map((member) => (
						<tr key={member.id}>
							<td style={{ padding: '8px', borderTop: '1px solid #e5e7eb' }}>{member.name}</td>
							<td style={{ padding: '8px', borderTop: '1px solid #e5e7eb' }}>{member.role}</td>
							<td style={{ padding: '8px', borderTop: '1px solid #e5e7eb' }}>{member.status}</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	);
};

export default MemberList;
