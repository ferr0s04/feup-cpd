#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <papi.h>

using namespace std;

#define SYSTEMTIME clock_t

void OnMult(int m_ar, int m_br) 
{
    SYSTEMTIME Time1, Time2;
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            pha[i*m_ar + j] = (double)1.0;

    for(i=0; i<m_br; i++)
        for(j=0; j<m_br; j++)
            phb[i*m_br + j] = (double)(i+1);

    Time1 = clock();

    for(i=0; i<m_ar; i++)
    {   for(j=0; j<m_br; j++)
        {   temp = 0;
            for(k=0; k<m_ar; k++)
            {   temp += pha[i*m_ar+k] * phb[k*m_br+j];
            }
            phc[i*m_ar+j] = temp;
        }
    }

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i=0; i<1; i++)
    {   for(j=0; j<min(10,m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultLine(int m_ar, int m_br) {
    SYSTEMTIME Time1, Time2;
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i * m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i * m_br + j] = i + 1.0;

    Time1 = clock();

    for(i = 0; i < m_ar; i++) {
        for(k = 0; k < m_ar; k++) {
            double a = pha[i * m_ar + k];
            for(j = 0; j < m_br; j++) {
                phc[i * m_br + j] += a * phb[k * m_br + j];
            }
        }
    }
    
    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++) {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultBlock(int m_ar, int m_br, int bkSize) {
    SYSTEMTIME Time1, Time2;
    char st[100];
    int i, j, k, ii, jj, kk;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i * m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i * m_br + j] = i + 1.0;

    Time1 = clock();

    for(ii = 0; ii < m_ar; ii += bkSize) {
        for(jj = 0; jj < m_br; jj += bkSize) {
            for(kk = 0; kk < m_ar; kk += bkSize) {
                for(i = ii; i < min(ii + bkSize, m_ar); i++) {
                    for(k = kk; k < min(kk + bkSize, m_ar); k++) {
                        double a = pha[i * m_ar + k];
                        for(j = jj; j < min(jj + bkSize, m_br); j++) {
                            phc[i * m_br + j] += a * phb[k * m_br + j];
                        }
                    }
                }
            }
        }
    }
    
    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++) {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void handle_error(int retval)
{
    printf("PAPI error %d: %s\n", retval, PAPI_strerror(retval));
    exit(1);
}

int main(int argc, char *argv[])
{
    if (argc < 3) {
        cout << "Usage: " << argv[0] << " <operation> <size> [blockSize]" << endl;
        cout << "Operation: 1=Multiplication, 2=Line Multiplication, 3=Block Multiplication" << endl;
        return 1;
    }

    int op = atoi(argv[1]);
    int lin = atoi(argv[2]);
    int col = lin;
    int blockSize = (argc == 4) ? atoi(argv[3]) : 0;

    int EventSet = PAPI_NULL;
    long long values[2];
    int ret;

    ret = PAPI_library_init(PAPI_VER_CURRENT);
    if (ret != PAPI_VER_CURRENT) cout << "FAIL" << endl;

    ret = PAPI_create_eventset(&EventSet);
    if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;

    ret = PAPI_add_event(EventSet, PAPI_L1_DCM);
    if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;

    ret = PAPI_add_event(EventSet, PAPI_L2_DCM);
    if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl;

    ret = PAPI_start(EventSet);
    if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;

    switch (op) {
        case 1:
            OnMult(lin, col);
            break;
        case 2:
            OnMultLine(lin, col);
            break;
        case 3:
            if (blockSize == 0) {
                cout << "Block size required for Block Multiplication." << endl;
                return 1;
            }
            OnMultBlock(lin, col, blockSize);
            break;
        default:
            cout << "Invalid operation." << endl;
            return 1;
    }

    ret = PAPI_stop(EventSet, values);
    if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
    printf("L1 DCM: %lld \n", values[0]);
    printf("L2 DCM: %lld \n", values[1]);

    PAPI_remove_event(EventSet, PAPI_L1_DCM);
    PAPI_remove_event(EventSet, PAPI_L2_DCM);
    PAPI_destroy_eventset(&EventSet);
    return 0;
}
