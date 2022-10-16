package com.jeanwest.reader.checkIn

import com.jeanwest.reader.shared.DraftProperties


val stockDraftProperties = DraftProperties(
    number = 119945,
    date = "1401/5/30",
    numberOfItems = 125,
    source = 44,
    destination = 1707
)

var stockDraftProducts = """
        [
  {
    "BarcodeMain_ID": "11987262",
    "kbarcode": "91533902J-2200-XL"
  },
  {
    "BarcodeMain_ID": "11987098",
    "kbarcode": "92573208J-2530-XL"
  },
  {
    "BarcodeMain_ID": "119810784",
    "kbarcode": "93551802-2011-160-1"
  },
  {
    "BarcodeMain_ID": "11982348",
    "kbarcode": "91573151J-2040-L"
  },
  {
    "BarcodeMain_ID": "11986789",
    "kbarcode": "91773118J-8550-L"
  },
  {
    "BarcodeMain_ID": "11988650",
    "kbarcode": "93159702-2035-M-1"
  },
  {
    "BarcodeMain_ID": "119811417",
    "kbarcode": "92581804J-2510-31"
  },
  {
    "BarcodeMain_ID": "119811570",
    "kbarcode": "91781708J-8520-29"
  },
  {
    "BarcodeMain_ID": "119810597",
    "kbarcode": "94531052J-2410-M"
  },
  {
    "BarcodeMain_ID": "11984848",
    "kbarcode": "92531013J-2700-L"
  },
  {
    "BarcodeMain_ID": "11987129",
    "kbarcode": "92573211J-2580-XXL"
  },
  {
    "BarcodeMain_ID": "119710578",
    "kbarcode": "81573102J-2580-XXXL"
  },
  {
    "BarcodeMain_ID": "11978556",
    "kbarcode": "81573102J-2670-M"
  },
  {
    "BarcodeMain_ID": "11982451",
    "kbarcode": "91573151J-2060-XXL"
  },
  {
    "BarcodeMain_ID": "11978557",
    "kbarcode": "81573102J-2690-M"
  },
  {
    "BarcodeMain_ID": "11978752",
    "kbarcode": "81573102J-2690-XXL"
  },
  {
    "BarcodeMain_ID": "11982455",
    "kbarcode": "91573151J-2380-XXL"
  },
  {
    "BarcodeMain_ID": "11982356",
    "kbarcode": "91573151J-2500-L"
  },
  {
    "BarcodeMain_ID": "11984116",
    "kbarcode": "91573151J-2310-XL"
  },
  {
    "BarcodeMain_ID": "11984925",
    "kbarcode": "91573151J-2710-M"
  },
  {
    "BarcodeMain_ID": "11982354",
    "kbarcode": "91573151J-2410-L"
  },
  {
    "BarcodeMain_ID": "11982365",
    "kbarcode": "91573151J-2060-XL"
  },
  {
    "BarcodeMain_ID": "11982349",
    "kbarcode": "91573151J-2060-L"
  },
  {
    "BarcodeMain_ID": "11982450",
    "kbarcode": "91573151J-2040-XXL"
  },
  {
    "BarcodeMain_ID": "11982340",
    "kbarcode": "91573151J-2500-M"
  },
  {
    "BarcodeMain_ID": "11984121",
    "kbarcode": "91573151J-2540-L"
  },
  {
    "BarcodeMain_ID": "11982375",
    "kbarcode": "91573151J-2800-XL"
  },
  {
    "BarcodeMain_ID": "11984916",
    "kbarcode": "91573151J-2280-XXL"
  },
  {
    "BarcodeMain_ID": "11984933",
    "kbarcode": "91573151J-2070-XL"
  },
  {
    "BarcodeMain_ID": "11978818",
    "kbarcode": "81573102J-2720-XXXL"
  },
  {
    "BarcodeMain_ID": "11978583",
    "kbarcode": "81573101J-2570-L"
  },
  {
    "BarcodeMain_ID": "119710243",
    "kbarcode": "81573101J-2390-L"
  },
  {
    "BarcodeMain_ID": "119710483",
    "kbarcode": "81573102J-2860-XXL"
  },
  {
    "BarcodeMain_ID": "11982417",
    "kbarcode": "92533031J-2580-L"
  },
  {
    "BarcodeMain_ID": "11982419",
    "kbarcode": "92533031J-2650-L"
  },
  {
    "BarcodeMain_ID": "119810573",
    "kbarcode": "94531052J-2020-M"
  },
  {
    "BarcodeMain_ID": "11978862",
    "kbarcode": "81531052J-2510-M"
  },
  {
    "BarcodeMain_ID": "11989744",
    "kbarcode": "94531052J-2510-XXXL"
  },
  {
    "BarcodeMain_ID": "11989186",
    "kbarcode": "92733303J-8510-L"
  },
  {
    "BarcodeMain_ID": "11987164",
    "kbarcode": "92732313J-8200-XL"
  },
  {
    "BarcodeMain_ID": "11984940",
    "kbarcode": "91573151J-2420-XXL"
  },
  {
    "BarcodeMain_ID": "11984937",
    "kbarcode": "91573151J-2420-M"
  },
  {
    "BarcodeMain_ID": "11984932",
    "kbarcode": "91573151J-2070-L"
  },
  {
    "BarcodeMain_ID": "11982451",
    "kbarcode": "91573151J-2060-XXL"
  },
  {
    "BarcodeMain_ID": "11982364",
    "kbarcode": "91573151J-2040-XL"
  },
  {
    "BarcodeMain_ID": "11982348",
    "kbarcode": "91573151J-2040-L"
  },
  {
    "BarcodeMain_ID": "11988634",
    "kbarcode": "93151788-2010-L-1"
  },
  {
    "BarcodeMain_ID": "9627927",
    "kbarcode": "74551702J-2010-L"
  },
  {
    "BarcodeMain_ID": "11982460",
    "kbarcode": "91573151J-2620-XXL"
  },
  {
    "BarcodeMain_ID": "11984123",
    "kbarcode": "91573151J-2540-XXL"
  },
  {
    "BarcodeMain_ID": "11982458",
    "kbarcode": "91573151J-2500-XXL"
  },
  {
    "BarcodeMain_ID": "11982324",
    "kbarcode": "91573151J-2500-S"
  },
  {
    "BarcodeMain_ID": "11978882",
    "kbarcode": "81531052J-2510-L"
  },
  {
    "BarcodeMain_ID": "11982875",
    "kbarcode": "92533031J-2710-L"
  },
  {
    "BarcodeMain_ID": "11982533",
    "kbarcode": "92533031J-2650-XXXL"
  },
  {
    "BarcodeMain_ID": "119718729",
    "kbarcode": "81751412J-8830-L"
  },
  {
    "BarcodeMain_ID": "119718725",
    "kbarcode": "81751412J-8020-L"
  },
  {
    "BarcodeMain_ID": "119710126",
    "kbarcode": "81551711J-2720-30"
  },
  {
    "BarcodeMain_ID": "119710446",
    "kbarcode": "81573101J-2540-XXL"
  },
  {
    "BarcodeMain_ID": "119710449",
    "kbarcode": "81573101J-2800-XXL"
  },
  {
    "BarcodeMain_ID": "11987624",
    "kbarcode": "91773123J-8380-M"
  },
  {
    "BarcodeMain_ID": "11986885",
    "kbarcode": "91573116J-2010-L"
  },
  {
    "BarcodeMain_ID": "11987105",
    "kbarcode": "92573208J-2720-XXL"
  },
  {
    "BarcodeMain_ID": "119811434",
    "kbarcode": "92581804J-2030-30"
  },
  {
    "BarcodeMain_ID": "11984814",
    "kbarcode": "92531010J-2620-XXL"
  },
  {
    "BarcodeMain_ID": "119810614",
    "kbarcode": "94531052J-2640-L"
  },
  {
    "BarcodeMain_ID": "11986693",
    "kbarcode": "92532018J-2500-L"
  },
  {
    "BarcodeMain_ID": "11982424",
    "kbarcode": "92533105J-2800-L"
  },
  {
    "BarcodeMain_ID": "119816120",
    "kbarcode": "94781956J-8510-29"
  },
  {
    "BarcodeMain_ID": "11978392",
    "kbarcode": "82533119J-2320-XL"
  },
  {
    "BarcodeMain_ID": "119811488",
    "kbarcode": "92581811J-2520-30"
  },
  {
    "BarcodeMain_ID": "11989796",
    "kbarcode": "94531052J-2770-XL"
  },
  {
    "BarcodeMain_ID": "11989709",
    "kbarcode": "94531052J-2010-L"
  },
  {
    "BarcodeMain_ID": "11984878",
    "kbarcode": "92531017J-2720-L"
  },
  {
    "BarcodeMain_ID": "119813917",
    "kbarcode": "84551933J-2580-34"
  },
  {
    "BarcodeMain_ID": "119813924",
    "kbarcode": "84551933J-2010-30"
  },
  {
    "BarcodeMain_ID": "11989948",
    "kbarcode": "94978221J-8170-F"
  },
  {
    "BarcodeMain_ID": "11983997",
    "kbarcode": "91919701-2010-XL-1"
  },
  {
    "BarcodeMain_ID": "1198576",
    "kbarcode": "91919801-2080-XL-1"
  },
  {
    "BarcodeMain_ID": "11983055",
    "kbarcode": "92851506J-2400-42"
  },
  {
    "BarcodeMain_ID": "119810362",
    "kbarcode": "94851409J-2200-41"
  },
  {
    "BarcodeMain_ID": "11984452",
    "kbarcode": "92871606J-8580-38"
  },
  {
    "BarcodeMain_ID": "11982297",
    "kbarcode": "92851506J-2400-44"
  },
  {
    "BarcodeMain_ID": "119721123",
    "kbarcode": "84851514J-2010-43"
  },
  {
    "BarcodeMain_ID": "119810402",
    "kbarcode": "94871604J-8700-40"
  },
  {
    "BarcodeMain_ID": "119810358",
    "kbarcode": "94851409J-2100-43"
  },
  {
    "BarcodeMain_ID": "11989373",
    "kbarcode": "94851401J-2580-45"
  },
  {
    "BarcodeMain_ID": "279551",
    "kbarcode": "28217007W0W02"
  },
  {
    "BarcodeMain_ID": "119718431",
    "kbarcode": "81919803-6151-M-1"
  },
  {
    "BarcodeMain_ID": "9621010",
    "kbarcode": "8871701601C04"
  },
  {
    "BarcodeMain_ID": "279551",
    "kbarcode": "28217007W0W02"
  },
  {
    "BarcodeMain_ID": "279540",
    "kbarcode": "28217007D9D01"
  },
  {
    "BarcodeMain_ID": "119812550",
    "kbarcode": "94972101J-8010-XL"
  },
  {
    "BarcodeMain_ID": "9623272",
    "kbarcode": "74872106J-8580-M"
  },
  {
    "BarcodeMain_ID": "975777",
    "kbarcode": "81912082-2510-120-1"
  },
  {
    "BarcodeMain_ID": "119812494",
    "kbarcode": "94852001J-2100-F"
  },
  {
    "BarcodeMain_ID": "11987565",
    "kbarcode": "92852001J-2580-F"
  },
  {
    "BarcodeMain_ID": "119810426",
    "kbarcode": "94872157J-8200-F"
  },
  {
    "BarcodeMain_ID": "119813019",
    "kbarcode": "94852001J-2905-F"
  },
  {
    "BarcodeMain_ID": "119813006",
    "kbarcode": "94852001J-2415-F"
  },
  {
    "BarcodeMain_ID": "119812529",
    "kbarcode": "94852001J-2625-F"
  },
  {
    "BarcodeMain_ID": "11987566",
    "kbarcode": "92852001J-2590-F"
  },
  {
    "BarcodeMain_ID": "11987567",
    "kbarcode": "92852001J-2010-F"
  },
  {
    "BarcodeMain_ID": "11982379",
    "kbarcode": "92490200J-2580-F"
  },
  {
    "BarcodeMain_ID": "976204",
    "kbarcode": "81912081-6427-120-1"
  },
  {
    "BarcodeMain_ID": "119812494",
    "kbarcode": "94852001J-2100-F"
  },
  {
    "BarcodeMain_ID": "119810425",
    "kbarcode": "94872156J-8010-F"
  },
  {
    "BarcodeMain_ID": "11987566",
    "kbarcode": "92852001J-2590-F"
  },
  {
    "BarcodeMain_ID": "11987567",
    "kbarcode": "92852001J-2010-F"
  },
  {
    "BarcodeMain_ID": "11987540",
    "kbarcode": "92852001J-2075-F"
  },
  {
    "BarcodeMain_ID": "11987567",
    "kbarcode": "92852001J-2010-F"
  },
  {
    "BarcodeMain_ID": "11987567",
    "kbarcode": "92852001J-2010-F"
  },
  {
    "BarcodeMain_ID": "119812995",
    "kbarcode": "94852001J-2080-F"
  },
  {
    "BarcodeMain_ID": "11987539",
    "kbarcode": "92852001J-2035-F"
  },
  {
    "BarcodeMain_ID": "119812995",
    "kbarcode": "94852001J-2080-F"
  },
  {
    "BarcodeMain_ID": "119812493",
    "kbarcode": "94852001J-2090-F"
  },
  {
    "BarcodeMain_ID": "11987206",
    "kbarcode": "92852001J-2100-F"
  },
  {
    "BarcodeMain_ID": "119812992",
    "kbarcode": "94852001J-2030-F"
  },
  {
    "BarcodeMain_ID": "119812492",
    "kbarcode": "94852001J-2070-F"
  },
  {
    "BarcodeMain_ID": "119812502",
    "kbarcode": "94852001J-2330-F"
  },
  {
    "BarcodeMain_ID": "119812501",
    "kbarcode": "94852001J-2200-F"
  },
  {
    "BarcodeMain_ID": "119812537",
    "kbarcode": "94852001J-2700-F"
  },
  {
    "BarcodeMain_ID": "975777",
    "kbarcode": "81912082-2510-120-1"
  },
  {
    "BarcodeMain_ID": "11989005",
    "kbarcode": "94950501J-2290-F"
  },
  {
    "BarcodeMain_ID": "119719570",
    "kbarcode": "84950124J-2010-F"
  }
]        
    """.trimIndent()
