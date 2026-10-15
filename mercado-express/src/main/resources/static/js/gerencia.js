/*
 * Graficos do /gerencia com Chart.js. Os dados chegam prontos do servidor
 * no <script id="dados-graficos" type="application/json">.
 *
 * Cores: verde-folha da marca para a serie principal; laranja para
 * "recusado" e azul para "lucro" (pares validados para daltonismo: verde x
 * vermelho NAO passa, por isso recusado nao e vermelho). Vermelho fica
 * reservado ao alerta de estoque baixo, que tambem tem icone + texto.
 */
(function () {
    if (typeof Chart === 'undefined') {
        return;
    }
    var dados = JSON.parse(document.getElementById('dados-graficos').textContent);

    var COR = {
        verde: '#0B6B3A',
        laranja: '#eb6834',
        azul: '#2a78d6',
        neutro: '#8A918C',
        alerta: '#d03b3b',
        texto: '#59615C',
        grade: '#E4E7E3',
        superficie: '#FFFFFF'
    };

    var real = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    var inteiro = new Intl.NumberFormat('pt-BR');

    Chart.defaults.font.family = 'Inter, system-ui, sans-serif';
    Chart.defaults.font.size = 12;
    Chart.defaults.color = COR.texto;
    Chart.defaults.maintainAspectRatio = false;
    Chart.defaults.plugins.legend.position = 'top';
    Chart.defaults.plugins.legend.align = 'start';
    Chart.defaults.plugins.legend.labels.boxWidth = 12;
    Chart.defaults.plugins.legend.labels.boxHeight = 12;
    Chart.defaults.plugins.tooltip.backgroundColor = '#14181A';
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.cornerRadius = 4;

    function eixoY(formatar) {
        return {
            beginAtZero: true,
            border: {display: false},
            grid: {color: COR.grade},
            ticks: {callback: function (v) { return formatar(v); }, maxTicksLimit: 6}
        };
    }
    var eixoX = {grid: {display: false}, border: {color: COR.grade}, ticks: {maxRotation: 0, autoSkipPadding: 12}};

    // 1) Vendas por dia: barras empilhadas (aprovados + recusados)
    new Chart(document.getElementById('g-vendas'), {
        type: 'bar',
        data: {
            labels: dados.dias,
            datasets: [
                {label: 'Aprovados', data: dados.aprovados, backgroundColor: COR.verde,
                    borderColor: COR.superficie, borderWidth: {top: 2}, borderRadius: 4, borderSkipped: 'bottom'},
                {label: 'Recusados', data: dados.recusados, backgroundColor: COR.laranja,
                    borderColor: COR.superficie, borderWidth: {top: 2}, borderRadius: 4, borderSkipped: 'bottom'}
            ]
        },
        options: {
            interaction: {mode: 'index', intersect: false},
            scales: {x: Object.assign({stacked: true}, eixoX), y: Object.assign({stacked: true}, eixoY(inteiro.format))},
            plugins: {tooltip: {callbacks: {label: function (c) { return ' ' + c.dataset.label + ': ' + c.parsed.y + ' pedido(s)'; }}}},
            datasets: {bar: {maxBarThickness: 22}}
        }
    });

    // 2) Faturamento x lucro: duas linhas, um eixo so (ambas em R$)
    new Chart(document.getElementById('g-faturamento'), {
        type: 'line',
        data: {
            labels: dados.dias,
            datasets: [
                {label: 'Faturamento', data: dados.faturamento, borderColor: COR.verde, backgroundColor: COR.verde,
                    borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, tension: 0.25},
                {label: 'Lucro', data: dados.lucro, borderColor: COR.azul, backgroundColor: COR.azul,
                    borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, tension: 0.25}
            ]
        },
        options: {
            interaction: {mode: 'index', intersect: false},
            scales: {x: eixoX, y: eixoY(function (v) { return real.format(v).replace(',00', ''); })},
            plugins: {tooltip: {callbacks: {label: function (c) { return ' ' + c.dataset.label + ': ' + real.format(c.parsed.y); }}}}
        }
    });

    // 3) Top produtos: barras horizontais, uma serie
    new Chart(document.getElementById('g-top'), {
        type: 'bar',
        data: {
            labels: dados.topNomes,
            datasets: [{label: 'Receita', data: dados.topReceita, backgroundColor: COR.verde,
                borderRadius: 4, borderSkipped: 'start', maxBarThickness: 18}]
        },
        options: {
            indexAxis: 'y',
            scales: {
                x: eixoY(function (v) { return real.format(v).replace(',00', ''); }),
                y: {grid: {display: false}, border: {display: false}}
            },
            plugins: {
                legend: {display: false},
                tooltip: {callbacks: {label: function (c) {
                    return ' ' + real.format(c.parsed.x) + ' · ' + dados.topQuantidade[c.dataIndex] + ' un.';
                }}}
            }
        }
    });

    // 4) Status dos pagamentos: rosca com 3 fatias (parte do todo)
    new Chart(document.getElementById('g-status'), {
        type: 'doughnut',
        data: {
            labels: ['Aprovado', 'Recusado', 'Pendente'],
            datasets: [{data: dados.status, backgroundColor: [COR.verde, COR.laranja, COR.neutro],
                borderColor: COR.superficie, borderWidth: 2}]
        },
        options: {
            cutout: '62%',
            plugins: {
                legend: {position: 'bottom', labels: {generateLabels: function (grafico) {
                    var base = Chart.overrides.doughnut.plugins.legend.labels.generateLabels(grafico);
                    base.forEach(function (item, i) { item.text = item.text + ' (' + dados.status[i] + ')'; });
                    return base;
                }}},
                tooltip: {callbacks: {label: function (c) {
                    var total = dados.status.reduce(function (a, b) { return a + b; }, 0) || 1;
                    return ' ' + c.label + ': ' + c.parsed + ' (' + Math.round(c.parsed * 100 / total) + '%)';
                }}}
            }
        }
    });

    // 5) Estoque por produto: barras; abaixo do minimo em vermelho de alerta
    new Chart(document.getElementById('g-estoque'), {
        type: 'bar',
        data: {
            labels: dados.estoqueNomes,
            datasets: [{
                label: 'Estoque', data: dados.estoqueQtd, borderRadius: 4, borderSkipped: 'bottom', maxBarThickness: 26,
                backgroundColor: dados.estoqueBaixo.map(function (baixo) { return baixo ? COR.alerta : COR.verde; })
            }]
        },
        options: {
            scales: {
                x: Object.assign({}, eixoX, {ticks: {autoSkip: false, maxRotation: 50, minRotation: 50}}),
                y: eixoY(inteiro.format)
            },
            plugins: {
                legend: {display: false},
                tooltip: {callbacks: {label: function (c) {
                    var i = c.dataIndex;
                    var texto = ' ' + c.parsed.y + ' un. · mínimo ' + dados.estoqueMinimo[i];
                    return dados.estoqueBaixo[i] ? texto + ' · ⚠ estoque baixo' : texto;
                }}}
            }
        }
    });
})();
