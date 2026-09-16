#!/usr/bin/env python3
"""Build the bundled 329-question knowledge asset from the reviewed PDF."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


BLOCKS = [
    ("test-1", "Тест 1", 164),
    ("test-2-1", "Тест 2 - блок 1", 29),
    ("test-2-2", "Тест 2 - блок 2", 72),
    ("test-2-3", "Тест 2 - блок 3", 64),
]

CATEGORY_RULES = [
    ("Пожарная безопасность", ("пожар", "огнетуш", "возгоран", "горени")),
    ("Охрана труда и электробезопасность", ("охрана труда", "опасн производ", "вредн производ", "электрическим ток", "сигнальн жилет", "служебн проход", "железнодорожных путях", "медицинск осмотр")),
    ("АЛСН и устройства безопасности", ("алсн", "эпк", "кж на локомотив", "кодов из рельсов", "устройств безопасности", "клуб", "саут")),
    ("Сигнализация и движение поездов", ("светофор", "сигнал", "автоблокиров", "днц", "дсп", "поезд", "перегон", "станци", "маршрутн указател", "неправильн путь")),
    ("Тормозное оборудование", ("тормоз", "кран машиниста", "воздухораспредел", "компрессор", "давлен", "магистрал", "тормозного цилиндр", "башмак")),
    ("Электрические машины и цепи", ("электр", "генератор", "двигател", "контактор", "реле", "аккумулятор", "изоляц", "цеп")),
    ("Дизель и системы тепловоза", ("дизел", "топлив", "масл", "охлаждающ", "тнвд", "коленчат", "фильтр")),
    ("Подвижной состав и экипажная часть", ("колесн", "бандаж", "букс", "рессор", "автосцеп", "тележ", "ось", "ползун", "гребн")),
]


def normalize(value: str) -> str:
    value = value.replace("\f", "\n")
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r" *\n *", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def category_for(question: str, answer: str) -> str:
    haystack = f"{question} {answer}".lower()
    for category, needles in CATEGORY_RULES:
        if any(needle in haystack for needle in needles):
            return category
    return "Эксплуатация и обслуживание"


def keywords_for(question: str, answer: str) -> list[str]:
    words = re.findall(r"[а-яёa-z0-9-]{4,}", f"{question} {answer}".lower())
    stop = {"какое", "какая", "какой", "каким", "каких", "сколько", "должен", "должна", "должны", "может", "является", "следующие", "укажите", "правильный", "ответ"}
    result: list[str] = []
    for word in words:
        if word not in stop and word not in result:
            result.append(word)
        if len(result) == 12:
            break
    return result


def parse_block(text: str, block_id: str, title: str, expected: int) -> list[dict]:
    pattern = re.compile(
        r"(?ms)^\s*(\d+)\.\s+(.*?)\n\s*Правильный ответ:\s*\n(.*?)(?=^\s*\d+\.\s+|\Z)"
    )
    records = []
    for number, question_raw, answer_raw in pattern.findall(text):
        question = normalize(question_raw)
        answer = normalize(answer_raw)
        category = category_for(question, answer)
        records.append({
            "id": f"{block_id}-q{int(number):03d}",
            "blockId": block_id,
            "blockTitle": title,
            "sourceNumber": int(number),
            "question": question,
            "correctAnswer": answer,
            "category": category,
            "keywords": keywords_for(question, answer),
            "requiresImage": bool(re.search(r"рисунк|изображен|показан.{0,12}схем", question.lower())),
            "diagnosticScenarioIds": [],
            "equipmentIds": [],
            "knowledgeTopics": [category],
            "source": "Вопросы_и_правильные_ответы_OCR_исправлено(1).pdf",
            "sourceVersion": "2026-09-16-reviewed",
        })
    if len(records) != expected:
        raise ValueError(f"{title}: parsed {len(records)}, expected {expected}")
    numbers = [item["sourceNumber"] for item in records]
    if len(numbers) != len(set(numbers)):
        raise ValueError(f"{title}: duplicate source numbers")
    return records


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: generate_exam_questions.py INPUT.pdf OUTPUT.json")
    pdf = Path(sys.argv[1])
    output = Path(sys.argv[2])
    extracted = subprocess.run(
        ["pdftotext", "-layout", str(pdf), "-"], check=True, capture_output=True, text=True
    ).stdout
    # ReportLab page footer: a standalone printed page number immediately before form feed.
    extracted = re.sub(r"(?m)^\s*\d+\s*\n\f", "\n\f", extracted)
    headers = list(re.finditer(r"(?m)^\s*Тест 1 - 164 вопроса|^\s*Тест 2 - блок [123] - \d+ вопрос", extracted))
    if len(headers) != 4:
        raise ValueError(f"found {len(headers)} test block headers, expected 4")
    questions: list[dict] = []
    for index, (block_id, title, expected) in enumerate(BLOCKS):
        start = headers[index].end()
        end = headers[index + 1].start() if index + 1 < len(headers) else len(extracted)
        questions.extend(parse_block(extracted[start:end], block_id, title, expected))
    if len(questions) != 329:
        raise ValueError(f"parsed {len(questions)}, expected 329")
    payload = {
        "schemaVersion": 1,
        "contentVersion": "2026-09-16-reviewed",
        "questionCount": len(questions),
        "distributionPolicy": "Exact Q&A in the unlocked section; categorized facts are also available to thematic application sections.",
        "questions": questions,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts: dict[str, int] = {}
    for item in questions:
        counts[item["category"]] = counts.get(item["category"], 0) + 1
    print(json.dumps({"questions": len(questions), "categories": counts}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
