from fastapi import FastAPI

app = FastAPI(title="같이 가요 - 그룹 서버")


@app.get("/health")
def health():
    return {"status": "ok"}
