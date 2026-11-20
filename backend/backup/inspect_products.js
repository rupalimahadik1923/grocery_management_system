const totalProds = db.products.countDocuments();
const missingStoreId = db.products.countDocuments({
  $or: [
    { storeId: { $exists: false } },
    { storeId: null },
    { storeId: '' }
  ]
});

const duplicateSkus = db.products.aggregate([
  { $group: { _id: "$sku", count: { $sum: 1 }, items: { $push: "$name" } } },
  { $match: { count: { $gt: 1 } } }
]).toArray();

print(JSON.stringify({
  totalProducts: totalProds,
  missingStoreIdCount: missingStoreId,
  duplicateSkuGroups: duplicateSkus
}, null, 2));
