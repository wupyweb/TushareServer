from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import Any, Dict
import tushare as ts

# 定义请求体模型
class RequestModel(BaseModel):
    api_name: str = Field(
        ...,      # ... 表示该字段是必填的
        description="API 名称，比如 'daily'",
        example="daily"
    )
    params: Dict[str, Any] = Field(
        ...,
        description="API 调用需要的参数，使用字典传递",
        example={"ts_code": "000001.SZ", "start_date": "20250401", "end_date": "20250421"}
    )
    fields: str = Field(
        "",
        description="需要返回的字段列表，逗号分隔，比如 'ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount'。默认空字符串，表示返回全部字段",
        example="ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount"
    )

# 定义返回体模型
class ResponseModel(BaseModel):
    code: int
    msg: str
    data: str

class TushareServer:
    def __init__(self):
        # 设置token
        ts.set_token("your_token")
        # 初始化pro接口
        self.api = ts.pro_api()
        # 创建 FastAPI 实例
        self.app = FastAPI()

server = TushareServer()

# 定义 POST 接口
@server.app.post("/api", response_model=ResponseModel)
async def handle_api(request: RequestModel):
    # print(f"收到请求: api_name={request.api_name}, params={request.params}, fields={request.fields}")
    try:
        df = server.api.query(
            api_name=request.api_name,
            fields=request.fields,
            **request.params
        )
        # 将 DataFrame 转换为 CSV 格式的字符串
        csv_data = df.to_csv(index=False)
    except Exception as e:
        return ResponseModel(
            code=1,
            msg=f"请求失败: {str(e)}",
            data=""
        )
    
    return ResponseModel(
        code=0,
        msg="请求成功",
        data=csv_data
    )

# 启动服务器命令：
# uvicorn tushare_server:server.app --reload
