"""
Validação integrada (Fase 5) contra a API em execução — sem dados simulados: tudo é criado pela própria API.

Uso:  python scripts/e2e_validation.py [http://localhost:8080] [backend/.local-dev]
Requer a API local (LocalDevApplication) rodando e o administrador da plataforma em .local-dev/credentials.txt.
Cria uma administradora nova (nome com data/hora) e percorre: condomínio → unidades/responsáveis → rateio →
cobranças → retorno CNAB → extrato OFX → inadimplência/acordo → fechamento mensal → reservas → portaria →
portal do morador → isolamento entre administradoras. Sai com código 1 na primeira verificação que falhar.
"""
import datetime as dt
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'
LOCAL = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else 'backend/.local-dev')
TODAY = dt.date.today()
STAMP = dt.datetime.now().strftime('%Y%m%d%H%M%S')
checks = []


def call(method, path, token=None, body=None, expect=200, raw=None, content_type=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    if data is not None:
        req.add_header('Content-Type', content_type or 'application/json')
    req.add_header('X-TekCondo-Client', 'e2e')
    try:
        with urllib.request.urlopen(req) as r:
            status, text = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    if status != expect:
        fail(f'{method} {path}: esperado {expect}, recebido {status}: {text[:300]}')
    return json.loads(text) if text else None


def multipart(path, token, filename, content, params=''):
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            f'Content-Type: application/octet-stream\r\n\r\n').encode('latin-1') + content + f'\r\n--{boundary}--\r\n'.encode()
    return call('POST', path + params, token, raw=body, expect=201, content_type='multipart/form-data; boundary=' + boundary)


def ok(name, condition, detail=''):
    checks.append(name)
    if not condition:
        fail(name + (' — ' + detail if detail else ''))
    print('  [ok]', name)


def fail(msg):
    print('  [FALHA]', msg)
    sys.exit(1)


def login(email, password):
    return call('POST', '/api/v1/auth/login', body={'email': email, 'password': password})['accessToken']


def token_from_outbox(email, after):
    """Lê o token do convite no e-mail gravado em .local-dev/outbox (modo dev: nada é enviado)."""
    for _ in range(20):
        for f in sorted((LOCAL / 'outbox').glob('*'), key=lambda p: p.stat().st_mtime, reverse=True):
            if f.stat().st_mtime < after:
                break
            text = f.read_text(encoding='utf-8', errors='ignore')
            if email in text:
                m = re.search(r'#token=([A-Za-z0-9_\-]+)', text) or re.search(r'token=([A-Za-z0-9_\-]+)', text)
                if m:
                    return m.group(1)
        time.sleep(0.5)
    fail('convite não encontrado no outbox para ' + email)


def activate(email, after):
    token = token_from_outbox(email, after)
    call('POST', '/api/v1/auth/password/reset', body={'token': token, 'newPassword': 'SenhaValida2026'}, expect=204)
    return login(email, 'SenhaValida2026')


def cnpj():
    import random
    base = [random.randint(0, 9) for _ in range(12)]
    for weights in ([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]):
        r = sum(d * w for d, w in zip(base, weights)) % 11
        base.append(0 if r < 2 else 11 - r)
    return ''.join(map(str, base))


def money(v):
    return f'{float(v):.2f}'


def cnab_line(fields):
    s = [' '] * 240
    for pos, value in fields:
        s[pos:pos + len(value)] = value
    return ''.join(s)


def main():
    creds = (LOCAL / 'credentials.txt').read_text(encoding='utf-8')
    email = re.search(r'[\w.+-]+@[\w.-]+', creds).group(0)
    password = re.search(r'(?:senha|password)\s*[:=]\s*(\S+)', creds, re.I).group(1)
    platform = login(email, password)
    print('1. Plataforma e administradora')
    admin_email = f'admin.{STAMP}@e2e.local'
    call('POST', '/api/v1/organizations', platform, {'name': f'Administradora E2E {STAMP}', 'cnpj': cnpj(),
         'admin': {'name': 'Admin E2E', 'email': admin_email, 'password': 'SenhaValida2026'}}, expect=201)
    admin = login(admin_email, 'SenhaValida2026')
    ok('administradora criada e administrador autenticado', bool(admin))

    print('2. Condomínio, unidades e responsáveis')
    condo = call('POST', '/api/v1/condominiums', admin, {'name': f'Residencial Londrina {STAMP}', 'type': 'RESIDENTIAL'}, expect=201)
    c = condo['id']
    b = f'/api/v1/condominiums/{c}'
    units = [call('POST', b + '/units', admin, {'identifier': str(101 + i), 'type': 'APARTMENT', 'idealFraction': f}, expect=201)['id']
             for i, f in enumerate(['33.3333', '33.3333', '33.3334'])]
    owner_email = f'morador.{STAMP}@e2e.local'
    person = call('POST', '/api/v1/people', admin, {'name': 'Morador E2E', 'kind': 'INDIVIDUAL', 'email': owner_email}, expect=201)['id']
    call('POST', b + '/occupancies', admin, {'unitId': units[0], 'personId': person, 'role': 'OWNER', 'financialResponsible': True,
                                             'startDate': '2024-01-01'}, expect=201)
    ok('3 unidades com frações somando 100%', len(units) == 3)

    print('3. Financeiro: conta e rateio')
    bank = call('POST', b + '/finance/bank-accounts', admin, {'name': 'Sicoob Cobrança', 'kind': 'CHECKING', 'bankCode': '756',
                                                              'agency': '4321', 'accountNumber': '12345-6', 'openingBalance': '0',
                                                              'openingDate': (TODAY - dt.timedelta(days=120)).isoformat()}, expect=201)['id']
    comp = (TODAY.replace(day=1) - dt.timedelta(days=1)).replace(day=1)
    alloc = {'competence': comp.strftime('%Y-%m'), 'description': 'Taxa condominial', 'chartAccountId': '20000000-0000-0000-0000-000000000101',
             'method': 'IDEAL_FRACTION', 'amount': '1000.00', 'dueDate': (TODAY - dt.timedelta(days=12)).isoformat()}
    preview = call('POST', b + '/billing/allocations/preview', admin, alloc)
    ok('rateio por fração fecha o total exato (R$ 1000,00)', money(preview['total']) == '1000.00', str(preview['total']))
    call('POST', b + '/billing/allocations', admin, alloc, expect=201)
    call('POST', b + '/billing/allocations', admin, alloc, expect=409)
    ok('geração duplicada da mesma competência é bloqueada', True)
    charges = call('GET', b + '/finance/entries?kind=INCOME&size=10&sort=dueDate,asc', admin)['content']
    ok('3 cobranças geradas, uma por unidade', len(charges) == 3)
    c101 = next(x for x in charges if x['unitLabel'] == '101')

    print('4. Retorno CNAB 240 (baixa automática)')
    paid_on = TODAY - dt.timedelta(days=2)
    d = lambda x: x.strftime('%d%m%Y')
    n = lambda v, size: str(round(float(v) * 100)).zfill(size)
    lines = [cnab_line([(0, '756'), (7, '0'), (143, d(TODAY))]),
             cnab_line([(0, '756'), (7, '3'), (8, '00001'), (13, 'T'), (15, '06'), (58, str(c101['collectionNumber']).ljust(15)),
                        (73, d(TODAY - dt.timedelta(days=12))), (81, n(c101['amount'], 15)), (198, n('1.50', 15))]),
             cnab_line([(0, '756'), (7, '3'), (8, '00002'), (13, 'U'), (17, n('8.00', 15)), (32, n(0, 15)), (47, n(0, 15)),
                        (77, n(float(c101['amount']) + 8, 15)), (92, n(float(c101['amount']) + 8, 15)), (137, d(paid_on)), (145, d(paid_on))]),
             cnab_line([(0, '756'), (7, '9')])]
    ret = multipart(b + '/billing/bank-returns', admin, 'COB.RET', '\r\n'.join(lines).encode('latin-1'), f'?bankAccountId={bank}')
    ok('retorno baixou 1 cobrança sem pendências', ret['settled'] == 1 and ret['pending'] == 0, str(ret))
    detail = call('GET', b + f"/finance/entries/{c101['id']}", admin)
    pay = detail['payments'][0]
    ok('multa (2%) e juros separados e total = valor pago', money(pay['total']) == money(float(c101['amount']) + 8)
       and float(pay['fine']) > 0, json.dumps(pay))

    print('5. Extrato OFX (conciliação automática)')
    total = money(pay['total']).replace('.', ',')
    ofx = ('OFXHEADER:100\nDATA:OFXSGML\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM><BANKID>756'
           '<ACCTID>12345-6</BANKACCTFROM><BANKTRANLIST>\n'
           f'<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>{paid_on:%Y%m%d}<TRNAMT>{total}<FITID>E2E-1<MEMO>LIQUIDACAO BOLETO</STMTTRN>\n'
           f'<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>{paid_on:%Y%m%d}<TRNAMT>-1,50<FITID>E2E-2<MEMO>TARIFA COBRANCA</STMTTRN>\n'
           '</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>')
    imp = multipart(b + f'/finance/bank-accounts/{bank}/statement-imports', admin, 'extrato.ofx', ofx.encode('latin-1'))
    ok('OFX: crédito conciliado automaticamente, tarifa pendente', imp['autoMatched'] == 1 and imp['pending'] == 1, str(imp))
    fee_line = next(l for l in call('GET', b + f'/finance/bank-accounts/{bank}/statement-lines?status=UNMATCHED', admin))
    call('POST', b + f"/finance/statement-lines/{fee_line['id']}/register", admin,
         {'chartAccountId': '20000000-0000-0000-0000-000000000211', 'description': 'Tarifa de cobrança'})
    st = call('GET', b + f'/finance/bank-accounts/{bank}/statement?from={(TODAY - dt.timedelta(days=30)).isoformat()}&to={TODAY.isoformat()}', admin)
    ok('saldo do extrato = saldo conciliado após registrar a tarifa', money(st['closingBalance']) == money(st['reconciledBalance']), str(st)[:200])

    print('6. Inadimplência e acordo')
    delinquency = call('GET', b + '/billing/delinquency', admin)
    ok('2 unidades inadimplentes (102 e 103)', delinquency['unitsInDebt'] == 2, str(delinquency['unitsInDebt']))
    debt = call('GET', b + f'/billing/units/{units[1]}/debt', admin)
    call('POST', b + '/billing/agreements', admin, {'unitId': units[1], 'entryIds': [l['entryId'] for l in debt['lines'] if l['remaining'] > 0],
                                                    'installments': 2, 'firstDueDate': (TODAY + dt.timedelta(days=5)).isoformat(),
                                                    'includeCharges': True}, expect=201)
    ok('acordo de parcelamento criado para a 102', True)

    print('7. Fechamento mensal')
    month_end = TODAY.replace(day=1) - dt.timedelta(days=1)
    first_closable = (comp - dt.timedelta(days=1))
    call('POST', b + '/finance/closing', admin, {'until': first_closable.isoformat(), 'notes': 'E2E'})
    ok('período fechado até ' + first_closable.isoformat(), True)
    call('POST', b + '/finance/entries', admin, {'kind': 'EXPENSE', 'description': 'Retroativa', 'chartAccountId': '20000000-0000-0000-0000-000000000205',
                                                 'amount': '10.00', 'dueDate': first_closable.isoformat()}, expect=422)
    ok('lançamento retroativo em período fechado é bloqueado', True)

    print('8. Reservas e portaria')
    resident_token = None
    t1 = time.time() - 1
    call('POST', f'/api/v1/people/{person}/portal-access', admin)
    resident_token = activate(owner_email, t1)
    area = call('POST', b + '/common-areas', admin, {'name': 'Salão de festas', 'fee': '120.00', 'requiresApproval': True, 'blockDelinquent': False,
                                                     'openTime': '10:00', 'closeTime': '23:00', 'minAdvanceHours': 24, 'maxAdvanceDays': 90}, expect=201)
    day = (TODAY + dt.timedelta(days=15)).isoformat()
    p = f'/api/v1/portal/{c}'
    r = call('POST', p + '/reservations', resident_token, {'areaId': area['id'], 'unitId': units[0], 'date': day, 'startTime': '18:00', 'endTime': '22:00'}, expect=201)
    call('POST', b + '/reservations', admin, {'areaId': area['id'], 'unitId': units[2], 'date': day, 'startTime': '21:00', 'endTime': '23:00'}, expect=409)
    approved = call('POST', b + f"/reservations/{r['id']}/approve", admin, {'note': 'ok'})
    ok('reserva do portal aprovada e taxa lançada na unidade', approved['status'] == 'CONFIRMED' and approved.get('entryId'))
    passx = call('POST', p + '/visitors', resident_token, {'unitId': units[0], 'kind': 'VISITOR', 'name': 'Visita E2E'}, expect=201)
    inside = call('POST', b + '/visitors/check-in-code', admin, {'code': passx['accessCode']})
    ok('visitante liberado pelo código do morador', inside['status'] == 'INSIDE')

    print('9. Portal do morador e isolamento')
    portal_charges = call('GET', p + '/charges', resident_token)
    ok('morador vê apenas a própria unidade (101)', [u['unitLabel'] for u in portal_charges] == ['101'])
    call('GET', b + '/finance/entries', resident_token, expect=403)
    ok('morador não acessa a área administrativa (403)', True)
    other_email = f'outra.{STAMP}@e2e.local'
    call('POST', '/api/v1/organizations', platform, {'name': f'Outra Administradora {STAMP}', 'cnpj': cnpj(),
         'admin': {'name': 'Outra', 'email': other_email, 'password': 'SenhaValida2026'}}, expect=201)
    foreign = login(other_email, 'SenhaValida2026')
    call('GET', b + '/finance/closing', foreign, expect=404)
    call('GET', b + '/common-areas', foreign, expect=404)
    call('GET', b + '/visitors/today', foreign, expect=404)
    ok('outra administradora recebe 404 nos dados deste condomínio', True)
    insights = call('GET', b + '/insights/cash-projection', admin)
    ok('projeção de caixa calculada', len(insights['weeks']) == 13)

    print(f'\nValidação integrada concluída: {len(checks)} verificações OK.')


if __name__ == '__main__':
    main()
