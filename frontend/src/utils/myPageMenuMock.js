export function getMockByKey(key) {
  switch (key) {
    case 'attendance':
      // 임시로 만들어둔 더미 데이터
      return {
        items: [
          {
            id: 'a1',
            title: '9월 29일(월) 세션',
            status: '출석 완료',
            time: '19:00',
          },
          { id: 'a2', title: '9월 22일(월) 세션', status: '결석', time: '-' },
        ],
      };

    case 'activity':
      return {
        items: [
          {
            id: 'ac3',
            content:
              '[마감일 기준 자동 우선순위 정렬 To Do 앱]에 댓글을 남겼어요.',
            time: '어제 오전 10:12',
          },
          {
            id: 'ac2',
            content:
              '[마감일 기준 자동 우선순위 정렬 To Do 앱]에 댓글을 남겼어요.',
            time: '어제 오전 10:12',
          },
          {
            id: 'ac1',
            content:
              '[마감일 기준 자동 우선순위 정렬 To Do 앱]글을 게시했어요.',
            time: '어제 오전 10:12',
          },
        ],
      };

    case 'points':
      return {
        items: [
          {
            id: 'p4',
            content: '출석',
            time: '어제 오전 10:12',
            point: +300,
          },
          {
            id: 'p3',
            content: '배팅',
            time: '어제 오전 10:12',
            point: +300,
          },
          {
            id: 'p2',
            content: '배팅 실패',
            time: '어제 오전 10:12',
            point: -100,
          },
          {
            id: 'p1',
            content: '출석',
            time: '어제 오전 10:12',
            point: +300,
          },
        ],
      };

    default:
      return { items: [] };
  }
}
