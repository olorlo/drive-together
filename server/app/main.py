from fastapi import FastAPI

app = FastAPI(title="따로, 또 같이 - 그룹 서버")


@app.get("/health")
def health():
    return {"status": "ok"}
