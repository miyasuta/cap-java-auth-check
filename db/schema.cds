namespace com.example;

using { managed } from '@sap/cds/common';

entity Orders : managed {
  key ID     : UUID;
  title      : String(100);
  amount     : Decimal(10,2);
  region     : String(10);
}
