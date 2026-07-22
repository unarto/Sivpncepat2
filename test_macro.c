#include <stdio.h>
#define STR_ARG(c) #c
#define STR(s) STR_ARG(s)
int main() {
    printf("PKG: %s/%s\n", STR(PKGNAME), STR(CLSNAME));
    return 0;
}
