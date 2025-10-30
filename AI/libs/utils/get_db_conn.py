import psycopg2

def get_db_conn(config: dict):
    """config에서 DB 접속 정보 가져와 psycopg2 Connection 생성"""
    conn = psycopg2.connect(
        host=config["host"],
        user=config["user"],
        password=config["password"],
        dbname=config["dbname"],
        port=config.get("port", 5432),
    )
    return conn