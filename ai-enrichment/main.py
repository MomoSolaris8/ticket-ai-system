from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from openai import AzureOpenAI
import os
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="AI Enrichment Service")

client = AzureOpenAI(
    azure_endpoint=os.getenv("AZURE_OPENAI_ENDPOINT", "https://your-resource.openai.azure.com/"),
    api_key=os.getenv("AZURE_OPENAI_API_KEY", ""),
    api_version="2024-02-01",
)
DEPLOYMENT_NAME = os.getenv("AZURE_OPENAI_DEPLOYMENT", "gpt-4o-mini")

SYSTEM_PROMPT = """You are a ticket classification assistant for an enterprise support system.
Analyze the ticket and return a JSON object with:
- category: one of [BILLING, TECHNICAL, COMPLIANCE, ACCOUNT, OTHER]
- priority: one of [LOW, MEDIUM, HIGH, CRITICAL]
- summary: a concise 1-2 sentence summary in English
- confidence_score: float between 0.0 and 1.0

Respond ONLY with valid JSON, no extra text."""


class EnrichRequest(BaseModel):
    ticket_id: str
    title: str
    description: str


class EnrichResponse(BaseModel):
    category: str
    priority: str
    summary: str
    confidence_score: float


@app.post("/enrich", response_model=EnrichResponse)
async def enrich_ticket(request: EnrichRequest):
    logger.info(f"Enriching ticket_id={request.ticket_id}")

    user_message = f"Title: {request.title}\nDescription: {request.description}"

    try:
        response = client.chat.completions.create(
            model=DEPLOYMENT_NAME,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
            temperature=0.1,
            max_tokens=256,
        )

        content = response.choices[0].message.content
        result = json.loads(content)

        logger.info(f"Enriched ticket_id={request.ticket_id} "
                    f"category={result['category']} priority={result['priority']}")

        return EnrichResponse(**result)

    except json.JSONDecodeError as e:
        logger.error(f"Failed to parse AI response for ticket_id={request.ticket_id}: {e}")
        raise HTTPException(status_code=502, detail="AI returned invalid response format")
    except Exception as e:
        logger.error(f"AI enrichment failed for ticket_id={request.ticket_id}: {e}")
        raise HTTPException(status_code=503, detail="AI enrichment service unavailable")


@app.get("/health")
def health():
    return {"status": "up"}
